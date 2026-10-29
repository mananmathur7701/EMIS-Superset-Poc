-- EMIS schema. Spring runs this file on every start, so every statement is safe to repeat.
-- The analytics.census_frozen view is not here: the backend rebuilds it from the field library
-- (AnalyticsViewService), because it has one column per number field.

-- Table of education districts.
CREATE TABLE IF NOT EXISTS district (
    -- Short district code, e.g. 'D03'.
    code VARCHAR(8) PRIMARY KEY,
    -- Full name, e.g. 'District Three'.
    name VARCHAR(120) NOT NULL
);

-- Table of schools.
CREATE TABLE IF NOT EXISTS school (
    -- Unique school id.
    id UUID PRIMARY KEY,
    -- School name.
    name VARCHAR(200) NOT NULL,
    -- Who runs the school.
    type VARCHAR(32) NOT NULL CHECK (type IN ('Government', 'Government-Assisted', 'Private')),
    -- Primary or secondary.
    level VARCHAR(16) NOT NULL CHECK (level IN ('Primary', 'Secondary')),
    -- The district the school is in now.
    district_code VARCHAR(8) NOT NULL REFERENCES district (code),
    -- False once the school closes.
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Table of everyone the system knows: officers, staff and students.
CREATE TABLE IF NOT EXISTS person (
    -- Unique person id.
    id UUID PRIMARY KEY,
    -- Full name.
    full_name VARCHAR(200) NOT NULL,
    -- Phone number.
    phone_number VARCHAR(32),
    -- False once the person leaves.
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Table of roles people hold. School roles fill school_id, a DEO fills district_code,
-- the Minister and CPU analysts fill neither (national).
CREATE TABLE IF NOT EXISTS school_role (
    -- Unique role id.
    id UUID PRIMARY KEY,
    -- The person holding the role.
    person_id UUID NOT NULL REFERENCES person (id),
    -- The kind of role.
    role VARCHAR(16) NOT NULL
        CHECK (role IN ('MINISTER', 'CPU_ANALYST', 'DEO', 'PRINCIPAL', 'REGISTRAR', 'TEACHER', 'STUDENT')),
    -- The school, for school roles.
    school_id UUID REFERENCES school (id),
    -- The district, for a DEO.
    district_code VARCHAR(8) REFERENCES district (code),
    -- First day of the role.
    start_date DATE NOT NULL,
    -- Last day of the role; empty while the role is current.
    end_date DATE,
    -- Make sure each kind of role fills in the right place, and only that place.
    CONSTRAINT school_role_place_chk CHECK (
        (role IN ('MINISTER', 'CPU_ANALYST') AND school_id IS NULL AND district_code IS NULL)
        OR (role = 'DEO' AND school_id IS NULL AND district_code IS NOT NULL)
        OR (role IN ('PRINCIPAL', 'REGISTRAR', 'TEACHER', 'STUDENT')
            AND school_id IS NOT NULL AND district_code IS NULL)),
    -- A role cannot end before it starts.
    CONSTRAINT school_role_dates_chk CHECK (end_date IS NULL OR end_date >= start_date)
);

-- Speed up "find this person's roles".
CREATE INDEX IF NOT EXISTS school_role_person_idx ON school_role (person_id);

-- Table of logins, one per person at most.
CREATE TABLE IF NOT EXISTS account (
    -- Unique account id.
    id UUID PRIMARY KEY,
    -- The person who owns the login.
    person_id UUID NOT NULL UNIQUE REFERENCES person (id),
    -- Login name.
    username VARCHAR(64) NOT NULL UNIQUE,
    -- BCrypt hash of the password; the plain password is never stored.
    password_hash VARCHAR(72) NOT NULL,
    -- False to block the login.
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- The field library: everything a form can collect. KPIs are built from number fields.
CREATE TABLE IF NOT EXISTS field (
    -- Unique field id.
    id UUID PRIMARY KEY,
    -- Short code; also the form question's key and the analytics column name. Only lowercase
    -- letters, digits and underscores, because it is used inside SQL.
    code VARCHAR(64) NOT NULL UNIQUE CHECK (code ~ '^[a-z][a-z0-9_]*$'),
    -- Display name.
    label VARCHAR(120) NOT NULL,
    -- Longer explanation.
    description VARCHAR(500),
    -- What kind of answer the field takes.
    field_type VARCHAR(16) NOT NULL DEFAULT 'NUMBER' CHECK (field_type IN ('NUMBER', 'TEXT', 'SELECT', 'DATE')),
    -- The choices of a SELECT field, comma-separated.
    options VARCHAR(1000),
    -- False to stop using the field in new KPIs and forms.
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- The KPI registry. A KPI is SUM(numerator) x multiplier, divided by SUM(denominator) if it has one.
CREATE TABLE IF NOT EXISTS kpi (
    -- Unique KPI id.
    id UUID PRIMARY KEY,
    -- Short code, e.g. 'KPI-TCH-01'.
    code VARCHAR(32) NOT NULL UNIQUE CHECK (code ~ '^[A-Z][A-Z0-9-]*$'),
    -- Display name.
    name VARCHAR(120) NOT NULL,
    -- Group, e.g. 'Teacher Development'.
    category VARCHAR(60),
    -- Longer explanation.
    description VARCHAR(500),
    -- 100 for a percentage, 1 for a ratio or a total.
    multiplier INTEGER NOT NULL CHECK (multiplier > 0),
    -- DRAFT while being defined, ACTIVE when in use, RETIRED when no longer used.
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    -- Who reports this KPI: school types, districts and hand-picked schools. Empty means all.
    scope_school_types TEXT[] NOT NULL DEFAULT '{}',
    scope_district_codes TEXT[] NOT NULL DEFAULT '{}',
    scope_school_ids UUID[] NOT NULL DEFAULT '{}',
    -- When the KPI was last changed.
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Links each KPI to its fields: always a NUMERATOR, optionally a DENOMINATOR. One field can feed
-- many KPIs.
CREATE TABLE IF NOT EXISTS kpi_field (
    -- The KPI.
    kpi_id UUID NOT NULL REFERENCES kpi (id),
    -- The field it uses.
    field_id UUID NOT NULL REFERENCES field (id),
    -- Which side of the ratio the field is on.
    part VARCHAR(16) NOT NULL CHECK (part IN ('NUMERATOR', 'DENOMINATOR')),
    -- One numerator and at most one denominator per KPI.
    PRIMARY KEY (kpi_id, part)
);

-- Forms built in the Form.io builder. A question whose key equals a field code fills that field.
CREATE TABLE IF NOT EXISTS form (
    -- Unique form id.
    id UUID PRIMARY KEY,
    -- Short code, e.g. 'CENSUS'.
    code VARCHAR(32) NOT NULL UNIQUE,
    -- Display name.
    name VARCHAR(120) NOT NULL,
    -- Longer explanation.
    description VARCHAR(500),
    -- DRAFT while being built; PUBLISHED forms are given to registrars.
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    -- The Form.io form definition (JSON).
    schema_json JSONB NOT NULL DEFAULT '{"display": "form", "components": []}',
    -- When the form was last changed.
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Which KPIs each form collects data for. One form can serve many KPIs, and the reverse.
CREATE TABLE IF NOT EXISTS form_kpi (
    -- The form.
    form_id UUID NOT NULL REFERENCES form (id),
    -- The KPI.
    kpi_id UUID NOT NULL REFERENCES kpi (id),
    -- Each pair only once.
    PRIMARY KEY (form_id, kpi_id)
);

-- A school's answers to one form for one academic year, and where it is in the review flow:
-- DRAFT (registrar) -> WITH_PRINCIPAL -> WITH_DEO -> WITH_CPU -> FROZEN (counts in dashboards).
CREATE TABLE IF NOT EXISTS form_return (
    -- Unique return id.
    id UUID PRIMARY KEY,
    -- The form answered.
    form_id UUID NOT NULL REFERENCES form (id),
    -- The school answering.
    school_id UUID NOT NULL REFERENCES school (id),
    -- Academic year, e.g. '2026/2027'.
    academic_year VARCHAR(9) NOT NULL CHECK (academic_year ~ '^[0-9]{4}/[0-9]{4}$'),
    -- Where the return is in the review flow.
    status VARCHAR(16) NOT NULL
        CHECK (status IN ('DRAFT', 'WITH_PRINCIPAL', 'WITH_DEO', 'WITH_CPU', 'FROZEN')),
    -- District, type and level copied from the school when the return was started, so old
    -- returns keep their district if the school later moves.
    district_code VARCHAR(8) NOT NULL REFERENCES district (code),
    school_type VARCHAR(32) NOT NULL,
    school_level VARCHAR(16) NOT NULL,
    -- When the return last changed.
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- One return per form, school and year.
    CONSTRAINT form_return_uk UNIQUE (form_id, school_id, academic_year)
);

-- One answer of a return: a number for NUMBER fields, text for the others.
CREATE TABLE IF NOT EXISTS form_return_value (
    -- The return.
    return_id UUID NOT NULL REFERENCES form_return (id),
    -- The field answered.
    field_id UUID NOT NULL REFERENCES field (id),
    -- The answer of a NUMBER field.
    number_value NUMERIC,
    -- The answer of a TEXT, SELECT or DATE field.
    text_value VARCHAR(1000),
    -- One answer per field per return.
    PRIMARY KEY (return_id, field_id)
);

-- The history of each return: who saved, submitted, approved or sent it back, and why.
CREATE TABLE IF NOT EXISTS form_return_event (
    -- Unique event id.
    id UUID PRIMARY KEY,
    -- The return.
    return_id UUID NOT NULL REFERENCES form_return (id),
    -- Who did it.
    person_id UUID REFERENCES person (id),
    -- What they did.
    action VARCHAR(16) NOT NULL CHECK (action IN ('SAVED', 'SUBMITTED', 'APPROVED', 'RETURNED')),
    -- Status before and after.
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    -- Their comment (required when sending back).
    comment VARCHAR(1000),
    -- When.
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Speed up "show this return's history".
CREATE INDEX IF NOT EXISTS form_return_event_return_idx ON form_return_event (return_id);

-- Which Superset dashboards each role may open (set by the CPU analyst).
CREATE TABLE IF NOT EXISTS dashboard_publication (
    -- Superset's dashboard id.
    dashboard_id INTEGER PRIMARY KEY,
    -- Its title, for lists.
    title VARCHAR(500) NOT NULL,
    -- Roles allowed to open it, e.g. {MINISTER,DEO}.
    roles TEXT[] NOT NULL DEFAULT '{}',
    -- When the publication last changed.
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Separate schema for what Superset may read (only emis_reader can see it; see database/setup.sql).
CREATE SCHEMA IF NOT EXISTS analytics;
