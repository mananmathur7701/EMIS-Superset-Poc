# EMIS Census Analytics: KPIs, forms, review flow and Superset dashboards (POC)

The CPU analyst defines KPIs, the fields they are built from, and the forms that collect those
fields (Form.io builder). Registrars fill the forms in, and each return goes through review:
principal, then DEO, then CPU analyst, then frozen. Frozen returns feed Apache Superset, where the
analyst builds dashboards and publishes each one to roles. The Minister, the CPU analyst, DEOs and
principals see those dashboards embedded in the EMIS app, limited to their own district or school
by Superset row-level security that the Java backend puts into each guest token.

```
Browser ── React app (:3000), styled like EMIS 2.0: login, then the pages the user's role allows
   │         Minister: Dashboards   CPU analyst: everything   DEO / Principal: Dashboards + Reviews
   │         Registrar: My forms    Teacher: nothing
   ├── /api/* ──> Vite dev server (npm run dev, :3000) ──> Spring Boot backend (:8081)
   ├── Dashboards ── @superset-ui/embedded-sdk + guest token ── embed.localhost:8088
   ├── Builder    ── <iframe> /emis/sso?token=… signs the analyst in to Superset ── localhost:8088
   └── Forms      ── Form.io builder (CPU analyst) and renderer (registrars, reviewers)
Backend ── KPIs, fields, forms, returns, review flow, dashboard publishing
        ── rebuilds analytics.census_frozen (one column per number field) and syncs Superset metrics
Postgres (yours, localhost:5432): one database, emis_superset_app
          public: EMIS tables │ analytics: census_frozen view │ superset: Superset's own tables
          Superset reads census data only from the view (frozen returns only), as emis_reader
```

## Run it (everything on this machine, no Docker)

Everything runs locally and uses **one Postgres database on this machine** (default
`emis_superset_app` on `localhost:5432`).

| What | Where | How to start it |
|------|-------|-----------------|
| Postgres | `localhost:5432` (yours) | already running |
| Superset 6.1 | http://localhost:8088 (and http://embed.localhost:8088 for embedded dashboards) | `./superset/local/start.sh` |
| Backend (Spring Boot) | http://localhost:8081 | IntelliJ run configuration **SupersetPoc**, or `cd backend && mvn spring-boot:run` |
| Frontend (React) | http://localhost:3000 | `cd frontend && npm run dev` (forwards `/api` to the backend on 8081) |

**First time only:**

1. **Create the database**, if it doesn't exist yet. The backend creates its tables and demo data
   in it on first start.
2. **Copy `.env.example` to `.env`** and fill in `DB_PASSWORD` (your Postgres password, used by the
   backend) and two long random passwords (`EMIS_READER_PASSWORD`, `SUPERSET_DB_PASSWORD`). `.env`
   is never committed. The backend reads it itself (`spring.config.import` in `application.yml`),
   and so do the Superset scripts (`superset/local/env.sh`).
3. **Prepare the database once, as a Postgres superuser.** This adds Superset's two logins and its
   own schema:

   ```bash
   set -a; . ./.env; set +a
   psql -h 127.0.0.1 -U postgres -d emis_superset_app \
        -v reader_password="$EMIS_READER_PASSWORD" -v superset_password="$SUPERSET_DB_PASSWORD" \
        -f database/setup.sql
   ```

4. **Install Superset** into `superset/.venv` (a few minutes; it uses the exact package versions
   in `superset/local/requirements.txt`), and prepare its tables and admin user:

   ```bash
   ./superset/local/setup.sh
   ```

**Every time**, in this order:

1. Start Superset: `./superset/local/start.sh` (leave it running; stop with Ctrl-C).
2. Start the backend from IntelliJ (**SupersetPoc**) or with `mvn spring-boot:run` in `backend/`.
3. Start the frontend: `npm run dev` in `frontend/`, then open http://localhost:3000.
4. First time only, after 1–3 are up: `./superset/local/provision.sh` connects Superset to the
   census data, copies the KPI formulas and builds the "Census KPIs" dashboard.

`superset/local/env.sh` holds every Superset setting and reads the passwords from `.env`. The
secrets have the same development defaults as `application.yml`, so the backend and Superset
match without extra setup. Change them for anything beyond a laptop demo.

Every username and password is listed in [ACCOUNTS.md](ACCOUNTS.md). For a step-by-step walk
through every class and function, with the data passed between them, see
[docs/APPLICATION_FLOW.md](docs/APPLICATION_FLOW.md).
For what every field in the chart builder means (line, bar, area, table, big number, pie), see
[docs/CHART_BUILDER_GUIDE.md](docs/CHART_BUILDER_GUIDE.md).
The whole journey from a new field and KPI to a dashboard query, as one PlantUML sequence diagram
(browser, backend, Superset and each database schema), is in
[docs/diagrams/field-kpi-dashboard-flow.puml](docs/diagrams/field-kpi-dashboard-flow.puml): paste it
into https://www.planttext.com to view it.

**Resetting the demo data.** Stop the backend, drop the EMIS tables and the view (`form_return_event,
form_return_value, form_return, form_kpi, form, dashboard_publication, kpi_field, kpi, field,
account, school_role, person, school, district` and `analytics.census_frozen`), then start the
backend: it creates the tables and the demo data again. The `superset` schema is not touched.

**Docker.** The Docker files (`docker-compose.yml` and the `Dockerfile`s) are from the earlier
setup and are no longer used.

## Logs: following every request

The backend writes a log line for every step, on the console (IntelliJ's Run window) and in files:

| File | Holds |
|------|-------|
| `logs/emis-backend.log` | Everything, a new file each day (kept 30 days) |
| `logs/users/<username>.log` | One user's activity only, e.g. `logs/users/kstrose.log`. Start-up lines go to `system.log`, requests before login to `anonymous.log` |

`logs/` is created in the folder the backend is started from (IntelliJ: the project root). Every
line shows the time, level, **request id**, **user**, the class that wrote it, and the message,
indented by how deep it is in the call chain:

```
02:15:07.412 INFO  [req 3f9a1c2e] [user kstrose] RequestLoggingFilter | → POST /api/admin/kpis (from 127.0.0.1)
02:15:07.413 INFO  [req 3f9a1c2e] [user kstrose] AdminController | ▶ AdminController.createKpi(KpiInput[code=KPI-ENR-02, …], session)
02:15:07.414 INFO  [req 3f9a1c2e] [user kstrose] UserSession |   ▶ UserSession.requireUsername(session)
02:15:07.415 INFO  [req 3f9a1c2e] [user kstrose] UserSession |   ◀ UserSession.requireUsername → "kstrose" (0 ms)
02:15:07.416 INFO  [req 3f9a1c2e] [user kstrose] KpiService |   ▶ KpiService.save(null, KpiInput[…])
02:15:07.417 DEBUG [req 3f9a1c2e] [user kstrose] KpiService |     validate(): checking code, name, multiplier, status and fields of KPI-ENR-02
…
02:15:07.590 INFO  [req 3f9a1c2e] [user kstrose] RequestLoggingFilter | ← POST /api/admin/kpis answered 200 in 178 ms
02:15:07.590 INFO  [req 3f9a1c2e] [user kstrose] RequestLoggingFilter | Call trace of POST /api/admin/kpis:
    AdminController.createKpi
      UserSession.requireUsername
      AccessService.requireViewer
      KpiService.save
        KpiService.listAll
      AnalyticsViewService.syncSuperset
        KpiService.findActiveKpis
        KpiService.allMetricNames
        SupersetClient.syncCensusDataset
```

- **▶ / ◀** mark a method starting and finishing, with its (masked) arguments, its result and its
  time. **✖** marks a refusal (e.g. 403) or an error; unexpected errors include the full Java
  stack trace, written once where they happened.
- The **Call trace** block at the end of each request is the whole chain in one place:
  endpoint → controller → services → client.
- **Passwords, tokens and password hashes are never written**: they appear as `****`,
  `eyJ…(token hidden)` and `(hash hidden)`.
- The **start-up report** lists what ran and what didn't. Each line is marked ✔ (fine), ⚠ (works,
  but needs attention) or ✘ (didn't work). It covers the database, the SQL scripts, the demo data
  counts, the analytics view, whether Superset is reachable and synced, the settings, every
  endpoint with the method behind it, and the log file paths.
- **The browser console** (F12 → Console, filter "EMIS") logs every backend call with the same
  request id (`[EMIS api] → POST /api/admin/kpis {requestId: "3f9a1c2e"}`), plus page changes,
  logins and each button the user presses.
- **Less detail:** set `LOG_LEVEL=INFO` (environment variable) to keep the ▶/◀ trace but drop the
  step-by-step DEBUG lines.

## The demo story

1. **Alicia Thomas** (`athomas`, registrar of Sample Primary School A) opens **My forms**. The
   Annual School Census for 2026/2027 is "Not started". She fills it in, saves a draft, and submits.
2. **Brenda Francis** (`bfrancis`, principal) finds it under **Reviews → Waiting for me** and approves.
3. **Marcia Felix** (`mfelix`, DEO of D03) can approve, or **send it back** one step with a
   required comment; the history shows who did what, and the comment.
4. **Kendell St. Rose** (`kstrose`, CPU analyst) gives the final approval, which **freezes** it.
5. On **Dashboards**, press **Refresh now** (or wait up to 15 minutes): School A's 2026/2027 figures
   appear for the Minister, Kendell, Marcia and Brenda, each limited to their own rows.

School B (D02) has its own principal (`mjoseph`) and registrar (`ncharles`) for a second run.

## What the CPU analyst can set up

| Page | What it does |
|------|--------------|
| **KPI registry** | Code, name, category, definition, status (Draft / Active / Retired), the formula (a numerator field, an optional denominator field, ×100 or ×1), and **who reports it**: school types, districts and hand-picked schools (empty means everyone). Active KPIs become Superset metrics straight away |
| **Field library** | Everything a form can ask: number, text, choice list or date. One field can feed many KPIs. A new number field becomes a column of `analytics.census_frozen` straight away |
| **Form builder** | The Form.io drag-and-drop builder, with a **Field library** group whose questions are already keyed to field codes. Link the form to KPIs (one form can serve many KPIs, and the reverse), and "Add questions for the ticked KPIs" adds a section per KPI. Publishing checks that every question's key is a field code. A school is not asked questions that only KPIs outside its scope need |
| **Chart builder** | Superset (Explore, charts, dashboards) in the page, signed in automatically as her own Superset user. **Full screen** gives Superset the whole window, so the chart preview has room |
| **Dashboard access** | Every Superset dashboard, with a checkbox per role (Minister, CPU analyst, DEO, Principal). Ticking a role also allows the EMIS app to embed that dashboard |
| **Reviews** | Every return in the country; the ones at the CPU step can be approved (frozen) or sent back |

## Logging in, roles and Superset access

- **Logging in.** Users log in with a username and password, checked against the BCrypt hash in
  `account`. The backend keeps the login in a session cookie.
- **Switching accounts.** A dropdown in the header switches to any other demo account straight
  away. Turn it off with `DEMO_SWITCH_ENABLED=false`.
- **Roles decide the pages.** The backend turns each user's current roles into capabilities
  (`DASHBOARDS`, `BUILD`, `MANAGE_KPIS`, `FILL_FORMS`, `REVIEW`), and every endpoint checks them.

| Username    | Role                              | Superset RLS clause        |
|-------------|-----------------------------------|----------------------------|
| `glaurent`  | Minister                          | none (all rows)            |
| `kstrose`   | CPU analyst                       | none (all rows)            |
| `mfelix`    | DEO, D03                          | `district_code = 'D03'`    |
| `pwilliams` | DEO, D02 (ended role: DEO, D03)   | `district_code = 'D02'`    |
| `bfrancis`  | Principal, Sample Primary School A | `school_id = '0ea77561-…'` |
| `mjoseph`   | Principal, Sample Primary School B | `school_id = '5bb7dce0-…'` |

**Dashboard access follows the login.** Guest tokens last 5 minutes. The embedded SDK renews them
in the background, but only while the login is valid and the dashboard is still published to the
user's role:

- **Sign out:** the dashboard is removed at once, and no new token is issued.
- **Idle timeout:** after `SESSION_IDLE_TIMEOUT` (default `30m`) without activity, the next
  renewal is refused and the user is sent back to the login page.
- **What counts as activity:** clicks, keys and scrolling on the page, clicking into the
  dashboard, and changing a dashboard filter. Background renewals do not count, so an unattended
  screen still times out.
- **The builder's Superset login ends too.** Signing out, timing out or switching away from the
  CPU analyst opens Superset's `/logout/` in a hidden frame. Superset's own login also expires
  after 8 hours at the latest.

## Checks

| Check | How to see it |
|-------|---------------|
| Each role gets its own pages | Switch accounts in the header: the Minister has Dashboards only, registrars My forms only, teachers "Nothing to show" |
| The whole review flow | Follow "The demo story" above; the tracker and history update at every step |
| Send back needs a reason | The "Send back" button stays disabled until a comment is typed; the API answers 400 without one |
| Only the right reviewer can act | Martin Joseph (School B) cannot approve School A's return; Patricia Williams (D02) cannot approve D03's (HTTP 403) |
| Draft and in-review returns never count | `analytics.census_frozen` only reads frozen returns |
| KPI scope hides questions | Make a KPI with scope "D02" on a new number field and link it to the census form: School B's registrar sees the question, School A's does not |
| New KPIs reach Superset | Save an active KPI, then open the Chart builder: it is in Explore's metric list |
| Sign-out ends Superset access | After "Sign out", `/api/analytics/auth/guest-token` answers 401 |
| Navigation doesn't reload Superset | Moving between pages keeps the dashboard and builder drawn; only "Refresh now" and the 15-minute timer reload the dashboard |

## Design notes

- **KPIs drive Superset, not the other way round.** Each KPI's formula (for example `SUM(trained_teachers) * 100.0 / NULLIF(SUM(total_teachers), 0)`) is saved as a Superset metric on the census dataset, when the backend starts, when a KPI or field is saved, and when `superset-provision` runs. Analysts pick "Trained teacher %" in Explore instead of retyping the formula. Every metric adds before it divides, so district figures are weighted by school size. A KPI without a denominator is a plain total.
- **The analytics view follows the field library.** `analytics.census_frozen` has one row per school and academic year and one column per number field, read from frozen returns only. The backend rewrites it (`AnalyticsViewService`) whenever number fields change, then makes Superset re-read the columns.
- **Forms store answers per field.** A Form.io question whose key is a field code fills that field (`form_return_value`). A return's district, school type and level are copied when it starts, so old returns keep their district if a school moves.
- **One step back.** Approving moves a return one step forward; sending it back moves it one step back (DEO → principal, principal → registrar), always with a comment. Every action is kept in `form_return_event`.
- **Dashboards are published per role.** The CPU analyst builds dashboards in Superset and ticks roles under Dashboard access (`dashboard_publication`). The first time anyone asks, the provisioned "Census KPIs" dashboard is published to every role.
- **Refreshing.** Superset's chart data cache is off (its default), so reloading an embedded dashboard always shows fresh figures. The Dashboards page reloads it every 15 minutes and on "Refresh now".
- **Automatic Superset sign-in.** The backend signs a one-minute token (HS256, shared `EMIS_SSO_SECRET`); a small route in `superset_config.py` (`/emis/sso`) checks it, creates the analyst's Superset user the first time (Admin role), signs her in and opens the requested page. In production, prefer a real single sign-on (for example OpenID Connect): the token travels in a URL, so it can appear in access logs during its minute.
- **One database for everything.** The backend, Superset's census reads and Superset's own tables all use `emis_superset_app` on your Postgres. Each login is limited to its own schema: the backend's login → `public`, `emis_reader` → `analytics`, `superset` → `superset`.
- **The builder hides Superset's nav in Explore with `uiConfig=4`, not `standalone=1`.** In Superset 6.1, `standalone=1` turns Explore into a bare chart with no control panel. The chart and dashboard lists keep Superset's menu.
- **The renderer uses a second hostname for the same Superset (`embed.localhost`).** Superset's `/embedded/<uuid>` page replaces the browser's Superset session with an anonymous one. On the same host as the builder, opening a dashboard would sign the analyst out of the builder.
- **Bootstrap only for Form.io.** Form.io draws with Bootstrap 5, so Bootstrap's CSS is loaded and its colours are mapped to the EMIS tokens. The app's own class names avoid Bootstrap's and Form.io's (`surface`, `side-nav`, `loading-cover`, …). Form.io itself loads only when a form is opened.
- **Navigation never reloads Superset.** The Dashboards and Chart builder pages stay loaded and are only hidden or shown, and `DASHBOARD_VIRTUALIZATION` is off. Other pages are drawn when opened, so they always show fresh data.
- **Stored query contexts.** Superset rejects guest chart requests whose columns, metrics or ordering differ from what is stored on the chart. Charts saved in Explore store theirs automatically; provisioning saves one for each chart it creates.

## Layout

Every line of code has a comment above it explaining what it does. For a plain-language walk
through every part, with real examples, see [docs/APPLICATION_FLOW.md](docs/APPLICATION_FLOW.md).

```
backend/                          Spring Boot 4.1 (Java 21), plain SQL through JdbcTemplate
  src/main/java/com/emis/analytics/
    EmisAnalyticsApplication.java     starts the app
    controller/                       answer HTTP requests (check who is asking, call a service)
      AuthController                    log in, log out, who am I, activity pings, demo account switch
      AnalyticsController               KPI catalogue, dashboards, guest tokens, builder sign-in links
      AdminController                   KPI registry, field library, forms, dashboard access (CPU analyst)
      ReturnController                  registrars' forms, reviewers' list, approve / send back
    service/                          the rules and the SQL
      AccessService                     passwords, current roles, capabilities and RLS clause
      UserSession                       who is logged in, and the idle timeout
      KpiService                        KPIs, their scope and their Superset formulas
      FieldService                      the field library
      FormService                       Form.io forms, their KPIs, and each school's version of a form
      ReturnService                     returns, answers and the review flow with its history
      DashboardService                  which dashboards each role may open
      AnalyticsViewService              rebuilds analytics.census_frozen and syncs Superset
      SsoTokenService                   the analyst's one-minute Superset sign-in token
    logging/                          request log, call trace, start-up report (see "Logs" above)
      RequestLoggingFilter              request id, user, endpoint, status, time, call trace per request
      CallTraceConfig / CallTraceInterceptor / CallTrace   ▶/◀ lines for every controller, service and client method
      LogSafety                         hides passwords, tokens and hashes in log lines
      StartupReport                     the ✔/⚠/✘ report after start-up
    client/                           calls to Superset
      SupersetClient                    Superset's REST API (guest tokens, dashboards, dataset sync)
      SupersetLogin                     what one Superset login returns
    config/                           SupersetProperties (the superset.* settings), CorsConfig
    model/                            data sent to the frontend: Viewer, Place, Kpi, KpiDetail, Field,
                                      FormSummary, FormDetail, FormRef, ReturnSummary, ReturnEvent,
                                      ReturnDetail, DashboardPublication
    dto/request/                      JSON bodies from the frontend: LoginRequest, SwitchRequest,
                                      KpiInput, FieldInput, FormInput, SaveReturnRequest,
                                      ReviewRequest, PublishRequest
    dto/response/                     GuestTokenResponse, BuilderResponse
  src/main/resources/               application.yml, logback-spring.xml (log outputs), schema.sql, data.sql
frontend/                         React 19 + Vite 8
  src/
    main.jsx                          starts the app (loads Bootstrap and Form.io styles, then ours)
    CensusAnalyticsDashboard.jsx      login check, idle tracking, and the EMIS shell with the role's pages
    styles.css                        EMIS 2.0 design tokens and layout
    api/                              api.js (calls /api, logs each call), useLoad.js (loads data when a page opens)
    logging/                          logger.js: "[EMIS …]" lines in the browser console
    auth/                             LoginScreen, RoleSwitcher, roles.js
    layout/                           Sidebar, pages.js (every page and the capability it needs)
    components/                       Loader, Modal, StatusBadge
    formio/                           FormioForm (renderer), FormioBuilder (designer), formio.js (helpers)
    pages/dashboards/                 DashboardsPage, EmbeddedAnalyticsWorkspace
    pages/builder/                    BuilderWorkspace
    pages/returns/                    MyFormsPage, ReviewsPage, ReturnView, ReturnTimeline
    pages/admin/                      KpiRegistryPage, FieldLibraryPage, FormBuilderPage, DashboardAccessPage
superset/                         superset_config.py (theme, embedding, /emis/sso), scripts/provision.py,
                                  local/ (env.sh, setup.sh, start.sh, provision.sh, requirements.txt),
                                  .venv/ (the local Python environment, created by setup.sh)
database/setup.sql                one-time setup of the database for Superset: its two logins and its schema
```
