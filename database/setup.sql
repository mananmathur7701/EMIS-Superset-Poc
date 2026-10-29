-- One-time setup that lets Superset use the EMIS database. Safe to run again.
-- Run it as a Postgres superuser, connected to the EMIS database (passwords come from .env):
--   set -a; . ./.env; set +a
--   psql -h 127.0.0.1 -U postgres -d emis_superset_app \
--        -v reader_password="$EMIS_READER_PASSWORD" -v superset_password="$SUPERSET_DB_PASSWORD" \
--        -f database/setup.sql
--
-- Afterwards the database has three schemas, each usable only by its own login:
--   public    EMIS tables           the backend's login (database owner / superuser)
--   analytics census_frozen view    emis_reader (Superset's read-only login for census data)
--   superset  Superset's own tables superset (Superset's own login)

-- Stop at the first error.
\set ON_ERROR_STOP on

-- Create Superset's read-only login for census data, if it does not exist yet.
SELECT 'CREATE ROLE emis_reader LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'emis_reader') \gexec
-- Set (or update) its password.
ALTER ROLE emis_reader PASSWORD :'reader_password';
-- Make every transaction of emis_reader read-only, as a second safety net.
ALTER ROLE emis_reader SET default_transaction_read_only = on;

-- Create Superset's own login, if it does not exist yet.
SELECT 'CREATE ROLE superset LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'superset') \gexec
-- Set (or update) its password.
ALTER ROLE superset PASSWORD :'superset_password';

-- Let both logins connect to this database (the one psql is connected to).
SELECT format('GRANT CONNECT ON DATABASE %I TO emis_reader, superset', current_database()) \gexec

-- Hide the EMIS tables in "public" from every login except the database owner and superusers.
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- The analytics schema holds the census_frozen view. The backend creates it; make sure it exists.
CREATE SCHEMA IF NOT EXISTS analytics;
-- Let Superset's reader look inside analytics ...
GRANT USAGE ON SCHEMA analytics TO emis_reader;
-- ... read the views that are there now ...
GRANT SELECT ON ALL TABLES IN SCHEMA analytics TO emis_reader;
-- ... and read every view the schema's owner (the backend's login) creates there later.
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA analytics GRANT SELECT ON TABLES TO emis_reader',
              pg_get_userbyid(nspowner))
FROM pg_namespace WHERE nspname = 'analytics' \gexec

-- Superset's own schema, owned by Superset's login.
CREATE SCHEMA IF NOT EXISTS superset AUTHORIZATION superset;
-- Whenever Superset's login connects to this database, put its tables in the superset schema.
SELECT format('ALTER ROLE superset IN DATABASE %I SET search_path = superset', current_database()) \gexec
