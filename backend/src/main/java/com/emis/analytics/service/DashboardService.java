// Services: the rules of the app and the SQL that reads and writes the database.
package com.emis.analytics.service;

// Imports: Java collections, logging and Spring's SQL and HTTP error helpers.
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
// Our own classes used here.
import com.emis.analytics.client.SupersetClient;
import com.emis.analytics.config.SupersetProperties;
import com.emis.analytics.model.DashboardPublication;
import com.emis.analytics.model.Viewer;

// Which Superset dashboards each role may open. The CPU analyst builds dashboards in Superset,
// then publishes each one to roles here.
@Service
public class DashboardService {

    // Logger for writing messages to the console.
    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    // The roles a dashboard can be published to.
    public static final List<String> VIEWER_ROLES = List.of("MINISTER", "CPU_ANALYST", "DEO", "PRINCIPAL");

    // Spring's helper for running SQL.
    private final JdbcTemplate jdbc;
    // Talks to Superset.
    private final SupersetClient superset;
    // The superset.* settings (for the default dashboard's slug).
    private final SupersetProperties settings;

    // Spring passes the helpers in when it creates this service.
    public DashboardService(JdbcTemplate jdbc, SupersetClient superset, SupersetProperties settings) {
        // Keep the SQL helper.
        this.jdbc = jdbc;
        // Keep the Superset client.
        this.superset = superset;
        // Keep the settings.
        this.settings = settings;
    }

    // The dashboards this user may open, in title order.
    public List<DashboardPublication> forViewer(Viewer viewer) {
        // The first time anyone asks, publish the provisioned census dashboard to every role.
        publishDefaultIfNone();
        // Now we will read the dashboards published to any of the user's roles.
        List<DashboardPublication> list = jdbc.query("""
                SELECT dashboard_id, title, roles FROM dashboard_publication
                WHERE roles && ?::text[] ORDER BY title
                """, (row, n) -> new DashboardPublication(row.getInt("dashboard_id"), row.getString("title"),
                List.of((String[]) row.getArray("roles").getArray())), (Object) viewer.roles().toArray(new String[0]));
        // Now we will log what this user may open.
        log.debug("forViewer(): roles {} may open {}", viewer.roles(), list.stream().map(DashboardPublication::title).toList());
        // Hand the list back.
        return list;
    }

    // Stops with HTTP 403 unless the dashboard is published to one of the user's roles.
    public void requireAllowed(Viewer viewer, int dashboardId) {
        // Look among the user's dashboards.
        boolean allowed = forViewer(viewer).stream().anyMatch(p -> p.dashboardId() == dashboardId);
        // Not there.
        if (!allowed) {
            // Now we will log the refusal.
            log.debug("requireAllowed(): dashboard {} is not published to {}", dashboardId, viewer.roles());
            // Send HTTP 403 Forbidden.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This dashboard is not published to your role.");
        }
    }

    // Every Superset dashboard with the roles it is published to, for the Dashboard access screen.
    public List<Map<String, Object>> listAll() {
        // Make sure the census dashboard has its default publication.
        publishDefaultIfNone();
        // Now we will read the saved publications: dashboard id -> roles.
        Map<Integer, List<String>> published = new LinkedHashMap<>();
        // Read each one.
        jdbc.query("SELECT dashboard_id, roles FROM dashboard_publication", row -> {
            // Remember its roles.
            published.put(row.getInt("dashboard_id"), List.of((String[]) row.getArray("roles").getArray()));
        });
        // The list we will return.
        List<Map<String, Object>> dashboards = new ArrayList<>();
        // Go through every dashboard in Superset.
        for (Map<String, Object> dashboard : superset.listDashboards()) {
            // Add its roles (none if it was never published).
            dashboard.put("roles", published.getOrDefault((Integer) dashboard.get("id"), List.of()));
            // Add it to the list.
            dashboards.add(dashboard);
        }
        // Return the list.
        return dashboards;
    }

    // Publishes a dashboard to the given roles (an empty list unpublishes it).
    public DashboardPublication publish(int dashboardId, String title, List<String> roles) {
        // Only known roles may be given.
        if (!Set.copyOf(VIEWER_ROLES).containsAll(roles)) {
            // Send HTTP 400.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Roles must be among " + VIEWER_ROLES);
        }
        // A published dashboard must be embeddable from our frontend.
        if (!roles.isEmpty()) superset.enableEmbedding(dashboardId);
        // Now we will save the publication (insert, or replace the roles).
        jdbc.update("""
                INSERT INTO dashboard_publication (dashboard_id, title, roles) VALUES (?, ?, ?::text[])
                ON CONFLICT (dashboard_id) DO UPDATE SET title = EXCLUDED.title, roles = EXCLUDED.roles,
                                                         updated_at = now()
                """, dashboardId, title, roles.toArray(new String[0]));
        // Now we will write to the console what was published.
        log.info("Dashboard {} ({}) published to {}", dashboardId, title, roles);
        // Return the publication.
        return new DashboardPublication(dashboardId, title, roles);
    }

    // If nothing has been published yet, publishes the provisioned census dashboard to every role.
    private void publishDefaultIfNone() {
        // Now we will count the publications.
        Integer count = jdbc.queryForObject("SELECT count(*) FROM dashboard_publication", Integer.class);
        // Something is published already: nothing to do.
        if (count != null && count > 0) return;
        // Now we will log that this is the first time.
        log.info("No dashboard is published yet: publishing '{}' to every role", settings.dashboardSlug());
        // Look up the census dashboard in Superset.
        Integer dashboardId = superset.dashboardIdBySlug(settings.dashboardSlug());
        // Not built yet (superset-provision has not run): nothing to publish.
        if (dashboardId == null) {
            // Now we will log why nothing happened.
            log.warn("Dashboard '{}' is not in Superset yet; run superset/local/provision.sh", settings.dashboardSlug());
            // Stop.
            return;
        }
        // Publish it to every role.
        publish(dashboardId, "Census KPIs", VIEWER_ROLES);
    }
}
