# Accounts

Local demo credentials only. Change every password in `.env` (see `.env.example`) before using
this anywhere other than your own machine.

## EMIS app accounts

Use these on the login page at http://localhost:3000 (the frontend started with `npm run dev`).
Every account has the password `Census#2026`. Only BCrypt hashes
are stored in the `account` table. After logging in, the header dropdown switches between them
without a password (demo only; turn it off with `DEMO_SWITCH_ENABLED=false`).

| Username    | Person               | Role                                         | Pages                                                        | Dashboard rows |
|-------------|----------------------|----------------------------------------------|--------------------------------------------------------------|----------------|
| `glaurent`  | Hon. Grace Laurent   | Minister of Education                        | Dashboards                                                   | All (national) |
| `kstrose`   | Kendell St. Rose     | CPU analyst                                  | Dashboards, Chart builder, Reviews (final step), KPI registry, Field library, Form builder, Dashboard access | All (national) |
| `mfelix`    | Marcia Felix         | DEO, District Three (D03)                    | Dashboards, Reviews (DEO step)                               | D03 only       |
| `pwilliams` | Patricia Williams    | DEO, District Two (D02)                      | Dashboards, Reviews (DEO step)                               | D02 only       |
| `bfrancis`  | Brenda Francis       | Principal, Sample Primary School A (D03)     | Dashboards, Reviews (principal step)                         | School A only  |
| `mjoseph`   | Martin Joseph        | Principal, Sample Primary School B (D02)     | Dashboards, Reviews (principal step)                         | School B only  |
| `athomas`   | Alicia Thomas        | Registrar, Sample Primary School A           | My forms                                                     | —              |
| `ncharles`  | Natalie Charles      | Registrar, Sample Primary School B           | My forms                                                     | —              |
| `kpierre`   | Kevin Pierre         | Teacher, Sample Primary School A             | Nothing                                                      | —              |
| `lmathurin` | Lisa Mathurin        | Teacher, Sample Primary School A             | Nothing                                                      | —              |
| `dhenry`    | Dwayne Henry         | Teacher, Sample Primary School B             | Nothing                                                      | —              |
| `sedwards`  | Sasha Edwards        | Teacher, Sample Primary School B             | Nothing                                                      | —              |

The two demo schools start the 2026/2027 census as "Not started", so the whole review flow can be
run: registrar → principal → DEO → CPU analyst → frozen (then it shows on dashboards).

## Superset

| Username  | Password | Used for |
|-----------|----------|----------|
| `admin`   | `admin`  | Superset's own login page (http://localhost:8088). The backend also uses it for guest tokens, publishing dashboards and syncing KPI metrics |
| `kstrose` | none     | Created the first time Kendell opens the Chart builder. She is signed in automatically by EMIS (no password, so it cannot be used on Superset's login page) |

## Postgres

Your own Postgres on `localhost:5432`, database `emis_superset_app`.

| Username      | Password | Used for |
|---------------|----------|----------|
| `postgres`    | yours: `DB_PASSWORD` in `.env` (read by the backend; never committed) | Superuser; the backend connects with it and owns the EMIS tables (`public` schema) |
| `emis_reader` | `EMIS_READER_PASSWORD` in `.env` | Superset's read-only login; can read only `analytics.census_frozen` |
| `superset`    | `SUPERSET_DB_PASSWORD` in `.env` | Superset's own tables, in the `superset` schema |

`database/setup.sql` creates `emis_reader` and `superset` with the passwords from `.env`. Change a
password by editing `.env` and running `setup.sql` again.
