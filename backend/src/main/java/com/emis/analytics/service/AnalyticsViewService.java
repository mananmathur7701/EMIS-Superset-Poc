// Services: the rules of the app and the SQL that reads and writes the database.
package com.emis.analytics.service;

// Imports: Java collections, logging and Spring's SQL, startup-event and transaction helpers.
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
// Our own classes used here.
import com.emis.analytics.client.SupersetClient;

// Builds analytics.census_frozen, the view Superset reads: one row per school and academic year,
// with one column per number field, from frozen returns only. It has to be rebuilt whenever number
// fields change, so the backend writes it instead of schema.sql.
@Service
public class AnalyticsViewService {

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(AnalyticsViewService.class);

    // What happened at the last Superset sync, for the start-up report ("OK: …" or "FAILED: …").
    private volatile String lastSyncResult = "not run yet";

    // The columns every row has, before the number fields. Row-level security uses school_id and
    // district_code, so they must always be there.
    public static final Set<String> FIXED_COLUMNS = Set.of(
            "school_id", "school_name", "academic_year", "district_code", "school_type", "school_level");

    // Friendly names Superset shows for the fixed columns.
    private static final Map<String, String> FIXED_LABELS = Map.of("school_id", "School ID",
            "school_name", "School", "academic_year", "Academic year", "district_code", "District",
            "school_type", "School type", "school_level", "School level");

    // Spring's helper for running SQL.
    private final JdbcTemplate jdbc;
    // Runs several SQL statements as one all-or-nothing transaction.
    private final TransactionTemplate transaction;
    // Reads the active KPIs for Superset's metrics.
    private final KpiService kpiService;
    // Talks to Superset.
    private final SupersetClient superset;

    // Spring passes the helpers in when it creates this service.
    public AnalyticsViewService(JdbcTemplate jdbc, TransactionTemplate transaction, KpiService kpiService,
                                SupersetClient superset) {
        // Keep the SQL helper.
        this.jdbc = jdbc;
        // Keep the transaction helper.
        this.transaction = transaction;
        // Keep the KPI service.
        this.kpiService = kpiService;
        // Keep the Superset client.
        this.superset = superset;
    }

    // Spring calls this once the backend has started (after schema.sql and data.sql ran), before
    // the start-up report (order 1 runs before order 2).
    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void onStartup() {
        // Now we will log the start-up step.
        log.info("Start-up: building the analytics view and syncing Superset");
        // Build the view and tell Superset about it.
        rebuildAndSync();
    }

    // What happened at the last Superset sync, e.g. "OK: 2 KPI formulas, 9 columns labelled".
    public String lastSyncResult() {
        // Hand it back.
        return lastSyncResult;
    }

    // Rebuilds the view, then refreshes Superset's dataset and KPI metrics.
    public void rebuildAndSync() {
        // Now we will log the two steps (they are calls inside this class, so the trace can't see them).
        log.debug("Step 1 of 2: rebuildView(), step 2 of 2: syncSuperset()");
        // Rebuild the view.
        rebuildView();
        // Tell Superset.
        syncSuperset();
    }

    // Writes the view again from the current number fields.
    public void rebuildView() {
        // Now we will read the codes of every number field, e.g. "total_pupils".
        List<String> codes = jdbc.queryForList(
                "SELECT code FROM field WHERE field_type = 'NUMBER' ORDER BY code", String.class);
        // Now we will log what the view will contain.
        log.debug("rebuildView(): one column per number field: {}", codes);
        // Start the SQL: the fixed columns of each school and year ...
        StringBuilder sql = new StringBuilder("""
                CREATE VIEW analytics.census_frozen AS
                SELECT r.school_id, s.name AS school_name, r.academic_year,
                       r.district_code, r.school_type, r.school_level""");
        // ... then one column per number field: that field's answer in the school's frozen returns.
        // Field codes are safe to paste into SQL: the database only accepts a-z, 0-9 and _ in them.
        for (String code : codes) {
            // e.g. ", MAX(v.number_value) FILTER (WHERE f.code = 'total_pupils') AS total_pupils"
            sql.append(",\n       MAX(v.number_value) FILTER (WHERE f.code = '").append(code)
                    .append("') AS ").append(code);
        }
        // Finish the SQL: frozen returns only, with their school, answers and fields.
        sql.append("""

                FROM form_return r
                JOIN school s ON s.id = r.school_id
                LEFT JOIN form_return_value v ON v.return_id = r.id
                LEFT JOIN field f ON f.id = v.field_id
                WHERE r.status = 'FROZEN'
                GROUP BY r.school_id, s.name, r.academic_year, r.district_code, r.school_type, r.school_level""");

        // Now we will swap the old view for the new one in one transaction, so Superset never
        // finds the view missing.
        transaction.executeWithoutResult(status -> {
            // Now we will log the swap.
            log.debug("rebuildView(): DROP VIEW + CREATE VIEW analytics.census_frozen + GRANT SELECT to emis_reader (one transaction)");
            // Remove the old view (its columns may differ).
            jdbc.execute("DROP VIEW IF EXISTS analytics.census_frozen");
            // Create the new one.
            jdbc.execute(sql.toString());
            // Let Superset's read-only login read it, if that login exists on this server.
            jdbc.execute("""
                    DO $$ BEGIN
                      IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'emis_reader') THEN
                        GRANT SELECT ON analytics.census_frozen TO emis_reader;
                      END IF;
                    END $$""");
        });
        // Now we will write to the console which columns the view has.
        log.info("Rebuilt analytics.census_frozen with number fields {}", codes);
    }

    // Refreshes Superset's census dataset and KPI metrics. Superset may not be running yet (for
    // example while everything starts), so a failure is only logged; superset-provision does the
    // same sync when it runs.
    public void syncSuperset() {
        // Now we will log the step.
        log.debug("syncSuperset(): sending columns, labels and KPI formulas to Superset");
        // Superset errors must not stop the backend, so catch them.
        try {
            // Friendly column names: the fixed ones plus every field's label.
            Map<String, String> labels = new LinkedHashMap<>(FIXED_LABELS);
            // Now we will add each field's label under its code.
            jdbc.query("SELECT code, label FROM field", row -> {
                // e.g. "total_pupils" -> "Total pupils on roll".
                labels.put(row.getString("code"), row.getString("label"));
            });
            // The active KPIs, which become Superset formulas.
            var activeKpis = kpiService.findActiveKpis();
            // Now we will send the columns and the active KPIs to Superset. Formulas of KPIs that
            // no longer exist at all (e.g. after a database reset) are removed.
            superset.syncCensusDataset(activeKpis, labels, kpiService.allMetricNames());
            // Remember the result for the start-up report.
            lastSyncResult = "OK: " + activeKpis.size() + " KPI formulas, " + labels.size() + " column labels";
            // Write to the console that it worked.
            log.info("Synced the census dataset and KPI metrics to Superset ({})", lastSyncResult);
        // Superset is down or not set up yet.
        } catch (Exception e) {
            // Remember the result for the start-up report.
            lastSyncResult = "FAILED: " + e.getMessage();
            // Write a warning to the console.
            log.warn("Could not sync Superset yet: {}", e.getMessage());
        }
    }
}
