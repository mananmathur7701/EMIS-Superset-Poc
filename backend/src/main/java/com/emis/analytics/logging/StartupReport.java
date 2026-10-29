// Logging: the request log, the call trace and the startup report.
package com.emis.analytics.logging;

// Imports: our classes, Java helpers, logging and Spring's startup event and web mappings.
import com.emis.analytics.client.SupersetClient;
import com.emis.analytics.config.SupersetProperties;
import com.emis.analytics.service.AnalyticsViewService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

// After start-up, writes one report of what ran and what did not: database, SQL scripts, demo
// data, the analytics view, Superset, settings, every endpoint, and where the log files are.
// ✔ = fine, ⚠ = works but needs attention, ✘ = did not work.
@Component
public class StartupReport {

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(StartupReport.class);

    // The default sign-in secret; using it is fine on a laptop, not elsewhere.
    private static final String DEV_SSO_SECRET = "dev-only-sso-secret-change-me-0123456789";

    // Spring's helper for running SQL.
    private final JdbcTemplate jdbc;
    // All settings (application.yml plus environment variables).
    private final Environment env;
    // The superset.* settings.
    private final SupersetProperties superset;
    // Talks to Superset.
    private final SupersetClient supersetClient;
    // Knows whether the startup Superset sync worked.
    private final AnalyticsViewService analyticsView;
    // Spring's list of every endpoint and the controller method behind it.
    private final RequestMappingHandlerMapping endpoints;

    // Spring passes the helpers in when it creates the report.
    public StartupReport(JdbcTemplate jdbc, Environment env, SupersetProperties superset, SupersetClient supersetClient,
                         AnalyticsViewService analyticsView,
                         @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping endpoints) {
        // Keep the SQL helper.
        this.jdbc = jdbc;
        // Keep the settings.
        this.env = env;
        // Keep the Superset settings.
        this.superset = superset;
        // Keep the Superset client.
        this.supersetClient = supersetClient;
        // Keep the view service.
        this.analyticsView = analyticsView;
        // Keep the endpoint list.
        this.endpoints = endpoints;
    }

    // Runs once the backend is ready, after AnalyticsViewService's own start-up work (order 2 > 1).
    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void report() {
        // The heading.
        log.info("================ EMIS backend: start-up report ================");
        // Each part checks one thing and logs ✔, ⚠ or ✘.
        reportDatabase();
        // The demo data and the analytics view.
        reportData();
        // Superset.
        reportSuperset();
        // The settings that change behaviour.
        reportSettings();
        // Every endpoint.
        reportEndpoints();
        // Where the logs go.
        reportLogFiles();
        // The closing line, with the address.
        log.info("================ Ready on http://localhost:{} ================", env.getProperty("local.server.port"));
        // The start-up calls are done; forget their trace.
        CallTrace.clear();
    }

    // Can we reach the database, and which one is it?
    private void reportDatabase() {
        // Catch errors so one failed check doesn't stop the report.
        try {
            // Now we will ask Postgres for its version.
            String version = jdbc.queryForObject("SELECT version()", String.class);
            // Log where we are connected, as whom, and the version (first words only).
            log.info("✔ Database: {} as {} ({})", env.getProperty("spring.datasource.url"),
                    env.getProperty("spring.datasource.username"), version.split(",")[0]);
            // The SQL scripts ran before this point, or start-up would have failed.
            log.info("✔ SQL scripts: schema.sql and data.sql ran (spring.sql.init.mode = {})",
                    env.getProperty("spring.sql.init.mode"));
        // The database could not be reached.
        } catch (Exception e) {
            // Log the problem.
            log.error("✘ Database: not reachable: {}", e.getMessage());
        }
    }

    // What is in the database: the demo data and the analytics view.
    private void reportData() {
        // Catch errors so one failed check doesn't stop the report.
        try {
            // Now we will count the main tables in one query.
            Map<String, Object> counts = jdbc.queryForMap("""
                    SELECT (SELECT count(*) FROM district) AS districts, (SELECT count(*) FROM school) AS schools,
                           (SELECT count(*) FROM account) AS accounts, (SELECT count(*) FROM field) AS fields,
                           (SELECT count(*) FROM kpi WHERE status = 'ACTIVE') AS active_kpis,
                           (SELECT count(*) FROM kpi) AS kpis,
                           (SELECT count(*) FROM form WHERE status = 'PUBLISHED') AS published_forms,
                           (SELECT count(*) FROM form) AS forms,
                           (SELECT count(*) FROM dashboard_publication) AS publications
                    """);
            // Log them.
            log.info("✔ Data: {} districts, {} schools, {} accounts, {} fields, {} KPIs ({} active), {} forms ({} published), {} published dashboards",
                    counts.get("districts"), counts.get("schools"), counts.get("accounts"), counts.get("fields"),
                    counts.get("kpis"), counts.get("active_kpis"), counts.get("forms"), counts.get("published_forms"),
                    counts.get("publications"));
            // Now we will count the returns per year and status.
            List<String> returns = jdbc.query("""
                    SELECT academic_year, status, count(*) AS n FROM form_return GROUP BY 1, 2 ORDER BY 1, 2
                    """, (row, n) -> row.getString("academic_year") + " " + row.getString("status") + " " + row.getInt("n"));
            // Log them.
            log.info("✔ Returns: {}", returns.isEmpty() ? "none" : String.join(", ", returns));
            // Now we will count the rows and read the columns of the analytics view.
            Integer viewRows = jdbc.queryForObject("SELECT count(*) FROM analytics.census_frozen", Integer.class);
            // Its columns, in order.
            List<String> columns = jdbc.queryForList("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema = 'analytics' AND table_name = 'census_frozen' ORDER BY ordinal_position
                    """, String.class);
            // Log them.
            log.info("✔ Analytics view: analytics.census_frozen has {} frozen school-years, columns {}", viewRows, columns);
        // A table or the view is missing.
        } catch (Exception e) {
            // Log the problem.
            log.error("✘ Data: could not read the demo data or the analytics view: {}", e.getMessage());
        }
    }

    // Is Superset running, and did the start-up sync work?
    private void reportSuperset() {
        // Now we will ask Superset's health page.
        boolean up = supersetClient.healthy();
        // Log the result.
        if (up) log.info("✔ Superset: reachable at {}", superset.baseUrl());
        // Not running: dashboards and the builder won't work until it is.
        else log.warn("✘ Superset: NOT reachable at {}. Start it with superset/local/start.sh", superset.baseUrl());
        // What happened when we tried to sync the census dataset and KPI formulas.
        String sync = analyticsView.lastSyncResult();
        // Log it.
        if (sync.startsWith("OK")) log.info("✔ Superset sync: {}", sync);
        // It failed or was skipped.
        else log.warn("✘ Superset sync: {} (it runs again when a KPI or field is saved)", sync);
    }

    // The settings that change how the app behaves.
    private void reportSettings() {
        // Port, reporting year, idle timeout and the demo switcher.
        log.info("✔ Settings: port {}, reporting year {}, idle timeout {}, demo account switcher {}",
                env.getProperty("local.server.port"), env.getProperty("emis.reporting-year"),
                env.getProperty("emis.session.idle-timeout"),
                "true".equals(env.getProperty("emis.demo-switch-enabled")) ? "ON" : "off");
        // Superset addresses.
        log.info("✔ Superset addresses: API {}, builder {}, embedded dashboards {}, embedding allowed from {}",
                superset.baseUrl(), superset.publicUrl(), superset.embedUrl(), superset.embedAllowedDomains());
        // Frontends allowed to call the API directly.
        log.info("✔ CORS: browser calls allowed from {}", env.getProperty("emis.cors-allowed-origins"));
        // Warn about the development sign-in secret.
        if (DEV_SSO_SECRET.equals(superset.ssoSecret())) {
            // Fine on a laptop.
            log.warn("⚠ Builder sign-in secret is the development default (EMIS_SSO_SECRET). Change it outside a laptop demo.");
        } else {
            // A real secret is set.
            log.info("✔ Builder sign-in secret: set");
        }
        // Warn about the demo switcher.
        if ("true".equals(env.getProperty("emis.demo-switch-enabled"))) {
            // Anyone logged in can become anyone else.
            log.warn("⚠ The demo account switcher is ON: any logged-in user can switch to any account (DEMO_SWITCH_ENABLED=false turns it off)");
        }
    }

    // Every endpoint and the controller method that answers it.
    private void reportEndpoints() {
        // One line per endpoint.
        List<String> lines = new ArrayList<>();
        // Go through Spring's list.
        endpoints.getHandlerMethods().forEach((info, handler) -> {
            // The HTTP method(s), e.g. "POST" (empty means any).
            String methods = info.getMethodsCondition().getMethods().isEmpty() ? "ANY"
                    : String.join(",", info.getMethodsCondition().getMethods().stream().map(Enum::name).toList());
            // The address(es), e.g. "/api/admin/kpis".
            String paths = String.join(",", info.getPatternValues());
            // e.g. "POST   /api/admin/kpis → AdminController.createKpi"
            lines.add(String.format("%-6s %-48s → %s.%s", methods, paths,
                    handler.getBeanType().getSimpleName(), handler.getMethod().getName()));
        });
        // Sort them by address.
        lines.sort((a, b) -> a.substring(7).compareTo(b.substring(7)));
        // Log them in one block.
        log.info("✔ Endpoints ({}):\n    {}", lines.size(), String.join("\n    ", lines));
    }

    // Where the log files are written.
    private void reportLogFiles() {
        // The folder, as an absolute path.
        Path folder = Path.of(env.getProperty("emis.logging.dir", "logs")).toAbsolutePath().normalize();
        // Log the files.
        log.info("✔ Log files: {} (everything), {} (one file per user: system, anonymous, kstrose, …)",
                folder.resolve("emis-backend.log"), folder.resolve("users/<username>.log"));
    }
}
