-- Demo data. Spring runs this file on every start. Every insert skips rows that already exist
-- (ON CONFLICT DO NOTHING), so changes made while the POC runs survive a restart.

-- Add the two districts.
INSERT INTO district (code, name)
VALUES ('D02', 'District Two'),
       ('D03', 'District Three')
-- Skip a district that is already there.
ON CONFLICT (code) DO NOTHING;

-- Add 25 schools: the first 13 are in District Three, the last 12 in District Two.
-- The two demo schools are Sample Primary School A (D03) and Sample Primary School B (D02).
INSERT INTO school (id, name, type, level, district_code, is_active)
VALUES ('0ea77561-3eaa-494c-bf84-3835196a1555', 'Sample Primary School A', 'Government',          'Primary',   'D03', TRUE),
       ('81920067-4099-405a-bf84-48741ef4ac6d', 'Morne Vert Primary',      'Government',          'Primary',   'D03', TRUE),
       ('6a785c42-999e-475f-bf6a-6f4f75f57210', 'Anse Claire Primary',     'Government-Assisted', 'Primary',   'D03', TRUE),
       ('07afcfbf-9af9-48ef-afbd-c217b15c9353', 'Belle Plaine Primary',    'Government',          'Primary',   'D03', TRUE),
       ('90bd4fec-94e7-49c1-8c5b-3891c5634dc1', 'Riviere Douce Primary',   'Government-Assisted', 'Primary',   'D03', TRUE),
       ('992239f8-4593-4b5a-aaae-ebddce82982b', 'Grande Savane Primary',   'Government',          'Primary',   'D03', TRUE),
       ('0c66d13f-6c15-43ee-b82a-24fbef66fed6', 'Cedar Hill Primary',      'Government',          'Primary',   'D03', TRUE),
       ('db5aec64-6f31-4962-9f2c-da3d0c0e4c54', 'Bon Air Preparatory',     'Private',             'Primary',   'D03', TRUE),
       ('3412ac5e-408d-4526-b991-49bfad3adff1', 'Hillview Preparatory',    'Private',             'Primary',   'D03', TRUE),
       ('de79448e-1a2e-41a2-b55e-c7000898044a', 'Mon Desir Secondary',     'Government',          'Secondary', 'D03', TRUE),
       ('72833de9-4822-489c-bed6-d4d9f7011846', 'Palm Ridge Secondary',    'Government-Assisted', 'Secondary', 'D03', TRUE),
       ('4d9a06ed-d635-4e0d-8cd4-82791a7a820b', 'La Pointe Secondary',     'Government',          'Secondary', 'D03', TRUE),
       ('77cebf88-f902-40e2-bfeb-f05a723b25d9', 'Seaview Academy',         'Private',             'Secondary', 'D03', TRUE),
       ('5bb7dce0-00bc-445b-9b7b-1df89e159d86', 'Sample Primary School B', 'Government',          'Primary',   'D02', TRUE),
       ('9bb79db9-646f-42eb-aa25-4a6989781cf1', 'Ravine Blanche Primary',  'Government',          'Primary',   'D02', TRUE),
       ('788e16b0-65c5-4aa0-b68a-71bb90a7d406', 'Bois Vert Primary',       'Government-Assisted', 'Primary',   'D02', TRUE),
       ('30105df5-cef9-4c1e-b92e-361a4b1df4c0', 'La Plaine Primary',       'Government',          'Primary',   'D02', TRUE),
       ('7d3daeb6-7c7b-49b3-96a1-b5d58f345b57', 'Trois Rivieres Primary',  'Government-Assisted', 'Primary',   'D02', TRUE),
       ('37a4928f-d847-40e7-8d4a-7a1caa5eedc8', 'Greenfield Primary',      'Government',          'Primary',   'D02', TRUE),
       ('abe9dfcb-ee6c-4ed4-bd0b-5023ead7a2fd', 'Mango Hill Primary',      'Government',          'Primary',   'D02', TRUE),
       ('b4a788d4-6878-4820-86d5-c42ef8d5fa68', 'Sunrise Montessori',      'Private',             'Primary',   'D02', TRUE),
       ('02480b1e-94bf-4356-9acf-19d6966623dc', 'Bocage Secondary',        'Government',          'Secondary', 'D02', TRUE),
       ('e1eb131e-ded1-4ca2-bf92-053bb408ccd8', 'Holy Trinity Secondary',  'Government-Assisted', 'Secondary', 'D02', TRUE),
       ('0b881897-fdcf-4ad9-ad01-2110b687d26b', 'Pointe Bleue Secondary',  'Government',          'Secondary', 'D02', TRUE),
       ('281ef76f-7337-4b28-9cbf-568b618f9b65', 'Harbourview College',     'Private',             'Secondary', 'D02', TRUE)
-- Skip a school that is already there.
ON CONFLICT (id) DO NOTHING;

-- Add the three census fields. total_teachers is one field used by both KPIs.
INSERT INTO field (id, code, label, description, field_type, is_active)
VALUES (gen_random_uuid(), 'total_pupils', 'Total pupils on roll',
        'Pupils on roll on the census reference date.', 'NUMBER', TRUE),
       (gen_random_uuid(), 'total_teachers', 'Total teachers employed',
        'Teachers employed on the census reference date.', 'NUMBER', TRUE),
       (gen_random_uuid(), 'trained_teachers', 'Trained teachers',
        'Teachers holding a recognised teaching qualification.', 'NUMBER', TRUE)
-- Skip a field that is already there.
ON CONFLICT (code) DO NOTHING;

-- Add the two census KPIs, active and reported by every school (empty scope = all).
INSERT INTO kpi (id, code, name, category, description, multiplier, status)
VALUES (gen_random_uuid(), 'KPI-TCH-01', 'Trained teacher %', 'Teacher Development',
        'Share of teachers who hold a recognised teaching qualification.', 100, 'ACTIVE'),
       (gen_random_uuid(), 'KPI-INF-01', 'Pupil-teacher ratio', 'Infrastructure',
        'Pupils on roll per teacher employed.', 1, 'ACTIVE')
-- Skip a KPI that is already there.
ON CONFLICT (code) DO NOTHING;

-- Link each KPI to its numerator and denominator fields.
INSERT INTO kpi_field (kpi_id, field_id, part)
-- Look up the KPI id and field id for each (KPI code, field code, part) below.
SELECT k.id, f.id, m.part
FROM (VALUES ('KPI-TCH-01', 'trained_teachers', 'NUMERATOR'),
             ('KPI-TCH-01', 'total_teachers',   'DENOMINATOR'),
             ('KPI-INF-01', 'total_pupils',     'NUMERATOR'),
             ('KPI-INF-01', 'total_teachers',   'DENOMINATOR')
     ) AS m (kpi_code, field_code, part)
-- Find the KPI by its code.
         JOIN kpi k ON k.code = m.kpi_code
-- Find the field by its code.
         JOIN field f ON f.code = m.field_code
-- Skip a link that is already there.
ON CONFLICT (kpi_id, part) DO NOTHING;

-- Add the published Annual School Census form (a Form.io definition): an Enrolment section and a
-- Teachers section. Each question's key is the field code it fills.
INSERT INTO form (id, code, name, description, status, schema_json)
VALUES ('b148277f-2065-45ee-8c92-5c9e38f754ee', 'CENSUS', 'Annual School Census',
        'Enrolment and teaching staff on the census reference date.', 'PUBLISHED',
        '{"display": "form", "components": [
           {"type": "panel", "key": "enrolment", "title": "Enrolment", "input": false, "components": [
             {"type": "number", "key": "total_pupils", "label": "Total pupils on roll", "input": true,
              "validate": {"required": true, "min": 0}}]},
           {"type": "panel", "key": "teachers", "title": "Teachers", "input": false, "components": [
             {"type": "number", "key": "total_teachers", "label": "Total teachers employed", "input": true,
              "validate": {"required": true, "min": 0}},
             {"type": "number", "key": "trained_teachers", "label": "Trained teachers", "input": true,
              "validate": {"required": true, "min": 0}}]}]}')
-- Skip the form if it is already there.
ON CONFLICT (id) DO NOTHING;

-- The census form collects the data of both KPIs.
INSERT INTO form_kpi (form_id, kpi_id)
-- Pair the census form with each KPI.
SELECT 'b148277f-2065-45ee-8c92-5c9e38f754ee', k.id FROM kpi k WHERE k.code IN ('KPI-TCH-01', 'KPI-INF-01')
-- Skip a link that is already there.
ON CONFLICT (form_id, kpi_id) DO NOTHING;

-- Census history as (school, year, status, pupils, teachers, trained teachers), held in a temporary
-- table for the next three statements. 2025/2026 is frozen everywhere. In 2026/2027 most schools
-- are frozen, four are still in the review flow (with typical mistakes in the drafts, such as La
-- Plaine's 3400-pupil typo), and the two demo schools have not started, so their registrars can
-- run the whole flow.
CREATE TEMP TABLE IF NOT EXISTS seed_census (
    school_name TEXT, academic_year TEXT, status TEXT,
    total_pupils INTEGER, total_teachers INTEGER, trained_teachers INTEGER);
-- Start from an empty list.
TRUNCATE seed_census;
-- The figures.
INSERT INTO seed_census
VALUES ('Sample Primary School A', '2025/2026', 'FROZEN',          248, 15, 11),
       ('Morne Vert Primary',      '2025/2026', 'FROZEN',          312, 17, 14),
       ('Morne Vert Primary',      '2026/2027', 'FROZEN',          325, 18, 15),
       ('Anse Claire Primary',     '2025/2026', 'FROZEN',          198, 11,  9),
       ('Anse Claire Primary',     '2026/2027', 'FROZEN',          205, 11,  9),
       ('Belle Plaine Primary',    '2025/2026', 'FROZEN',          156, 10,  8),
       ('Belle Plaine Primary',    '2026/2027', 'FROZEN',          149,  9,  8),
       ('Riviere Douce Primary',   '2025/2026', 'FROZEN',          274, 15, 12),
       ('Riviere Douce Primary',   '2026/2027', 'WITH_DEO',        281, 16, 13),
       ('Grande Savane Primary',   '2025/2026', 'FROZEN',          132,  8,  7),
       ('Grande Savane Primary',   '2026/2027', 'FROZEN',          128,  8,  7),
       ('Cedar Hill Primary',      '2025/2026', 'FROZEN',          176, 11, 10),
       ('Cedar Hill Primary',      '2026/2027', 'FROZEN',          182, 11, 10),
       ('Bon Air Preparatory',     '2025/2026', 'FROZEN',           96,  7,  3),
       ('Bon Air Preparatory',     '2026/2027', 'FROZEN',          104,  8,  4),
       ('Hillview Preparatory',    '2025/2026', 'FROZEN',           84,  6,  2),
       ('Hillview Preparatory',    '2026/2027', 'DRAFT',            90,  6,  0),
       ('Mon Desir Secondary',     '2025/2026', 'FROZEN',          642, 41, 33),
       ('Mon Desir Secondary',     '2026/2027', 'FROZEN',          655, 42, 35),
       ('Palm Ridge Secondary',    '2025/2026', 'FROZEN',          518, 34, 27),
       ('Palm Ridge Secondary',    '2026/2027', 'FROZEN',          530, 35, 29),
       ('La Pointe Secondary',     '2025/2026', 'FROZEN',          734, 46, 38),
       ('La Pointe Secondary',     '2026/2027', 'FROZEN',          721, 46, 39),
       ('Seaview Academy',         '2025/2026', 'FROZEN',          286, 22, 12),
       ('Seaview Academy',         '2026/2027', 'FROZEN',          298, 23, 13),
       ('Sample Primary School B', '2025/2026', 'FROZEN',          288, 16, 13),
       ('Ravine Blanche Primary',  '2025/2026', 'FROZEN',          214, 13, 10),
       ('Ravine Blanche Primary',  '2026/2027', 'FROZEN',          220, 13, 11),
       ('Bois Vert Primary',       '2025/2026', 'FROZEN',          167, 10,  8),
       ('Bois Vert Primary',       '2026/2027', 'FROZEN',          171, 10,  8),
       ('La Plaine Primary',       '2025/2026', 'FROZEN',          352, 19, 16),
       ('La Plaine Primary',       '2026/2027', 'DRAFT',          3400, 19, 17),
       ('Trois Rivieres Primary',  '2025/2026', 'FROZEN',          141,  9,  7),
       ('Trois Rivieres Primary',  '2026/2027', 'FROZEN',          138,  9,  7),
       ('Greenfield Primary',      '2025/2026', 'FROZEN',          236, 14, 12),
       ('Greenfield Primary',      '2026/2027', 'FROZEN',          244, 14, 12),
       ('Mango Hill Primary',      '2025/2026', 'FROZEN',          119,  8,  6),
       ('Mango Hill Primary',      '2026/2027', 'WITH_CPU',        115,  7,  6),
       ('Sunrise Montessori',      '2025/2026', 'FROZEN',           78,  6,  2),
       ('Sunrise Montessori',      '2026/2027', 'FROZEN',           85,  6,  3),
       ('Bocage Secondary',        '2025/2026', 'FROZEN',          688, 44, 36),
       ('Bocage Secondary',        '2026/2027', 'FROZEN',          702, 45, 38),
       ('Holy Trinity Secondary',  '2025/2026', 'FROZEN',          545, 36, 30),
       ('Holy Trinity Secondary',  '2026/2027', 'FROZEN',          552, 36, 31),
       ('Pointe Bleue Secondary',  '2025/2026', 'FROZEN',          476, 32, 25),
       ('Pointe Bleue Secondary',  '2026/2027', 'FROZEN',          489, 33, 27),
       ('Harbourview College',     '2025/2026', 'FROZEN',          312, 24, 14),
       ('Harbourview College',     '2026/2027', 'FROZEN',          320, 25, 15);

-- Create one census return per row, copying district, type and level from the school.
INSERT INTO form_return (id, form_id, school_id, academic_year, status, district_code, school_type, school_level)
-- New id, the census form, the school and the row's year and status.
SELECT gen_random_uuid(), 'b148277f-2065-45ee-8c92-5c9e38f754ee', s.id, c.academic_year, c.status,
       s.district_code, s.type, s.level
FROM seed_census c
-- Find each school by its name.
         JOIN school s ON s.name = c.school_name
-- Skip a return that is already there.
ON CONFLICT (form_id, school_id, academic_year) DO NOTHING;

-- Store each return's three answers.
INSERT INTO form_return_value (return_id, field_id, number_value)
-- Pick the figure that belongs to each field.
SELECT r.id, f.id,
       CASE f.code WHEN 'total_pupils' THEN c.total_pupils
                   WHEN 'total_teachers' THEN c.total_teachers
                   ELSE c.trained_teachers END
FROM seed_census c
-- Find the school ...
         JOIN school s ON s.name = c.school_name
-- ... and the return just created for it.
         JOIN form_return r ON r.school_id = s.id AND r.academic_year = c.academic_year
                           AND r.form_id = 'b148277f-2065-45ee-8c92-5c9e38f754ee'
-- Once for each of the three census fields.
         CROSS JOIN field f
WHERE f.code IN ('total_pupils', 'total_teachers', 'trained_teachers')
-- Skip an answer that is already there.
ON CONFLICT (return_id, field_id) DO NOTHING;

-- Add the demo people: the Minister, the CPU analyst, two DEOs, and for each demo school a
-- principal, a registrar and two teachers.
INSERT INTO person (id, full_name, phone_number, is_active)
VALUES ('d4632d77-4dc9-4c77-9e27-ae84330101d9', 'Hon. Grace Laurent', '+1 758 555 0100', TRUE),
       ('19a6a079-f928-4e06-becc-86dccd0224b2', 'Kendell St. Rose',   '+1 758 555 0101', TRUE),
       ('b6c63c9d-4984-4c1e-9020-a51a9e388154', 'Marcia Felix',       '+1 758 555 0102', TRUE),
       ('84ce6de4-65aa-40c3-9f58-30d43f3377bc', 'Patricia Williams',  '+1 758 555 0103', TRUE),
       ('2d396795-c6ef-4111-9bfe-ad75b359bde6', 'Brenda Francis',     '+1 758 555 0104', TRUE),
       ('2d3c4ed6-4c6f-4939-97f9-74ff58111082', 'Alicia Thomas',      '+1 758 555 0105', TRUE),
       ('99c063e8-c199-439d-9572-5af8d67a7e4b', 'Martin Joseph',      '+1 758 555 0106', TRUE),
       ('6c64568a-19d4-4345-9c30-512968e3ee64', 'Natalie Charles',    '+1 758 555 0107', TRUE),
       ('9a9279e5-56e9-4537-bc64-76716d5ef4da', 'Kevin Pierre',       '+1 758 555 0108', TRUE),
       ('ac2e9fc9-43a2-4858-b5d2-67d6bf778d1f', 'Lisa Mathurin',      '+1 758 555 0109', TRUE),
       ('8bd73b59-69e0-4d55-9a27-90cc2357ac94', 'Dwayne Henry',       '+1 758 555 0110', TRUE),
       ('fedbd346-7d13-4580-b823-2a47f890f280', 'Sasha Edwards',      '+1 758 555 0111', TRUE)
-- Skip a person who is already there.
ON CONFLICT (id) DO NOTHING;

-- Give each person a login. Only the BCrypt hash of the password is stored
-- (all passwords are listed in ACCOUNTS.md).
INSERT INTO account (id, person_id, username, password_hash, is_active)
VALUES (gen_random_uuid(), 'd4632d77-4dc9-4c77-9e27-ae84330101d9', 'glaurent',
        '$2a$10$Re.40GEsQ82zgov8avnE0.Fy7Ytcz2pQ4ApNCVNgaoD18qlK8R3Dm', TRUE),
       (gen_random_uuid(), '19a6a079-f928-4e06-becc-86dccd0224b2', 'kstrose',
        '$2a$10$HehXd/zbYfVh9zFostjieeC5IeNX9syNpBaBOAbBJ1qrkq6QfwOtu', TRUE),
       (gen_random_uuid(), 'b6c63c9d-4984-4c1e-9020-a51a9e388154', 'mfelix',
        '$2a$10$jXZlrNLbMwNTQcrKRAELGO4lTnSkZDW9Sr6tcDioSd6oH7s2rLdWe', TRUE),
       (gen_random_uuid(), '84ce6de4-65aa-40c3-9f58-30d43f3377bc', 'pwilliams',
        '$2a$10$Ns3U7tcuRePBvu/G9RegF.SbgoqlKk2wVAeTlmQtzeoQ6dIMRk2v6', TRUE),
       (gen_random_uuid(), '2d396795-c6ef-4111-9bfe-ad75b359bde6', 'bfrancis',
        '$2a$10$refUpkisjRC6oO8whiTWPehkDI6SuTXpmG/eknBhwKyK0hY.JJSeO', TRUE),
       (gen_random_uuid(), '2d3c4ed6-4c6f-4939-97f9-74ff58111082', 'athomas',
        '$2a$10$2Sj36T3.m4/zSzuAsWCbAuPWKEnIC9vbx0c8fO6rAh7.Vkr3Nk9Ae', TRUE),
       (gen_random_uuid(), '99c063e8-c199-439d-9572-5af8d67a7e4b', 'mjoseph',
        '$2a$10$KwupmWCqrPR9Y1HjjMcW7ubsD1v3m/57Bx.GsayMkQQb74w23qz1K', TRUE),
       (gen_random_uuid(), '6c64568a-19d4-4345-9c30-512968e3ee64', 'ncharles',
        '$2a$10$.86yzmhuUU8qHLBxmRC4Jeu4vJQM4clG3HSJpOWn.ny0zT/8c6AVu', TRUE),
       (gen_random_uuid(), '9a9279e5-56e9-4537-bc64-76716d5ef4da', 'kpierre',
        '$2a$10$qAQ8jf9sPOt.e.N/mQpaH.l4e6aQodMqA8LLe5q5lfSc7lkInXeky', TRUE),
       (gen_random_uuid(), 'ac2e9fc9-43a2-4858-b5d2-67d6bf778d1f', 'lmathurin',
        '$2a$10$D87.WQYcc2axa5PgNuFL.ugLr5nwyl7.cSths0zEppXfJTf1pBJIm', TRUE),
       (gen_random_uuid(), '8bd73b59-69e0-4d55-9a27-90cc2357ac94', 'dhenry',
        '$2a$10$KPrccTULFbUQpptd80ZFA.g6b1tJQcGT8rSJHZWQ5/vVdBTBPQYZO', TRUE),
       (gen_random_uuid(), 'fedbd346-7d13-4580-b823-2a47f890f280', 'sedwards',
        '$2a$10$XYlJedxORQ4BE/Zy39z8Fu5oAPNfyO/p3y465SYXyv9CaQLvJqd2m', TRUE)
-- Skip a login that is already there.
ON CONFLICT (username) DO NOTHING;

-- Add the roles. Ended roles are kept as history: Marcia was a principal before becoming DEO,
-- and Patricia ran District Three before moving to District Two. Only current roles give access.
INSERT INTO school_role (id, person_id, role, school_id, district_code, start_date, end_date)
VALUES ('9f8e129d-b490-46b0-a9ae-412d4b33d28f', 'd4632d77-4dc9-4c77-9e27-ae84330101d9', 'MINISTER',
        NULL, NULL, '2024-08-01', NULL),
       ('1c6df564-c278-4967-96c1-df0696d56c2f', '19a6a079-f928-4e06-becc-86dccd0224b2', 'CPU_ANALYST',
        NULL, NULL, '2021-09-01', NULL),
       ('5ff375b6-2a18-4da8-841a-037480d00a6a', 'b6c63c9d-4984-4c1e-9020-a51a9e388154', 'PRINCIPAL',
        '81920067-4099-405a-bf84-48741ef4ac6d', NULL, '2016-09-01', '2023-08-31'),
       ('bfcf9d2f-b9a2-4787-b4cd-15fccdd4aa6b', 'b6c63c9d-4984-4c1e-9020-a51a9e388154', 'DEO',
        NULL, 'D03', '2023-09-01', NULL),
       ('110c6841-77db-4336-ab52-1f8d84507e1e', '84ce6de4-65aa-40c3-9f58-30d43f3377bc', 'DEO',
        NULL, 'D03', '2018-09-01', '2022-01-09'),
       ('5d2c2586-0e41-4e6c-b51e-605301b4bd41', '84ce6de4-65aa-40c3-9f58-30d43f3377bc', 'DEO',
        NULL, 'D02', '2022-01-10', NULL),
       ('13075005-14c0-4f17-a648-5f178e8eca4a', '2d396795-c6ef-4111-9bfe-ad75b359bde6', 'PRINCIPAL',
        '0ea77561-3eaa-494c-bf84-3835196a1555', NULL, '2020-09-01', NULL),
       ('a3f1c2d4-5b6e-4f70-8a91-b2c3d4e5f607', '2d3c4ed6-4c6f-4939-97f9-74ff58111082', 'REGISTRAR',
        '0ea77561-3eaa-494c-bf84-3835196a1555', NULL, '2022-09-01', NULL),
       ('7acf71e1-50da-4c71-9f16-000087e2f369', '99c063e8-c199-439d-9572-5af8d67a7e4b', 'PRINCIPAL',
        '5bb7dce0-00bc-445b-9b7b-1df89e159d86', NULL, '2019-09-01', NULL),
       ('474fc71a-f351-4382-bed0-1a098d3c4175', '6c64568a-19d4-4345-9c30-512968e3ee64', 'REGISTRAR',
        '5bb7dce0-00bc-445b-9b7b-1df89e159d86', NULL, '2023-09-01', NULL),
       ('451e299e-f6b7-491b-bcc9-b26ca615c8a7', '9a9279e5-56e9-4537-bc64-76716d5ef4da', 'TEACHER',
        '0ea77561-3eaa-494c-bf84-3835196a1555', NULL, '2021-09-01', NULL),
       ('3db6983d-4989-4936-b3bc-f35ebc56c827', 'ac2e9fc9-43a2-4858-b5d2-67d6bf778d1f', 'TEACHER',
        '0ea77561-3eaa-494c-bf84-3835196a1555', NULL, '2024-09-01', NULL),
       ('0cde3747-3e10-438a-8449-f875b3f21cfc', '8bd73b59-69e0-4d55-9a27-90cc2357ac94', 'TEACHER',
        '5bb7dce0-00bc-445b-9b7b-1df89e159d86', NULL, '2020-09-01', NULL),
       ('902cc67b-86bd-4d06-a505-3949a21393f5', 'fedbd346-7d13-4580-b823-2a47f890f280', 'TEACHER',
        '5bb7dce0-00bc-445b-9b7b-1df89e159d86', NULL, '2025-09-01', NULL)
-- Skip a role that is already there.
ON CONFLICT (id) DO NOTHING;
