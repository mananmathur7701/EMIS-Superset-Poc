// Services: the rules of the app and the SQL that reads and writes the database.
package com.emis.analytics.service;

// Imports: JDBC rows and arrays, Java collections and Spring's SQL, HTTP error and transaction helpers.
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
// Our own classes used here.
import com.emis.analytics.dto.request.KpiInput;
import com.emis.analytics.model.Kpi;
import com.emis.analytics.model.KpiDetail;

// The KPI registry: the public catalogue (for Superset) and the CPU analyst's list and editor.
@Service
public class KpiService {

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(KpiService.class);

    // The analytics columns every KPI can be sliced by in Superset.
    private static final List<String> SLICE_COLUMNS =
            List.of("district_code", "academic_year", "school_level", "school_type");
    // The KPI statuses.
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "RETIRED");

    // SQL that is true when the KPI "k" covers the school "s" (an empty scope list means "all").
    // Other services paste it into their own queries.
    public static final String KPI_COVERS_SCHOOL = """
            (cardinality(k.scope_school_types) = 0 OR s.type = ANY (k.scope_school_types))
            AND (cardinality(k.scope_district_codes) = 0 OR s.district_code = ANY (k.scope_district_codes))
            AND (cardinality(k.scope_school_ids) = 0 OR s.id = ANY (k.scope_school_ids))
            """;

    // SQL for the public catalogue: active KPIs whose numerator field is active. The denominator
    // is optional (a KPI without one is a total).
    private static final String ACTIVE_KPIS_SQL = """
            -- Take the KPI's own columns ...
            SELECT k.id, k.code, k.name, k.description, k.multiplier,
                   -- ... plus the code of the field on top ...
                   numerator_field.code AS numerator_code,
                   -- ... plus the code of the field on the bottom, if there is one.
                   denominator_field.code AS denominator_code
            -- Start from the KPI table.
            FROM kpi k
            -- Find the NUMERATOR link and its field, which must be active.
            JOIN kpi_field numerator_link ON numerator_link.kpi_id = k.id AND numerator_link.part = 'NUMERATOR'
            JOIN field numerator_field ON numerator_field.id = numerator_link.field_id AND numerator_field.is_active
            -- Find the DENOMINATOR link and its field, if any.
            LEFT JOIN kpi_field denominator_link ON denominator_link.kpi_id = k.id AND denominator_link.part = 'DENOMINATOR'
            LEFT JOIN field denominator_field ON denominator_field.id = denominator_link.field_id
            -- Keep only active KPIs, and drop any whose denominator field was switched off.
            WHERE k.status = 'ACTIVE' AND (denominator_link.kpi_id IS NULL OR denominator_field.is_active)
            -- Return them in code order.
            ORDER BY k.code
            """;

    // SQL for the CPU analyst's list: every KPI with its fields and scope.
    private static final String ALL_KPIS_SQL = """
            -- Take every column of the KPI ...
            SELECT k.*,
                   -- ... its numerator field ...
                   n.field_id AS numerator_field_id, nf.code AS numerator_code,
                   -- ... and its denominator field, if any.
                   d.field_id AS denominator_field_id, df.code AS denominator_code
            FROM kpi k
            -- Find the NUMERATOR link and field.
            LEFT JOIN kpi_field n ON n.kpi_id = k.id AND n.part = 'NUMERATOR'
            LEFT JOIN field nf ON nf.id = n.field_id
            -- Find the DENOMINATOR link and field.
            LEFT JOIN kpi_field d ON d.kpi_id = k.id AND d.part = 'DENOMINATOR'
            LEFT JOIN field df ON df.id = d.field_id
            -- In code order.
            ORDER BY k.code
            """;

    // Spring's helper for running SQL.
    private final JdbcTemplate jdbc;

    // Spring passes the JdbcTemplate in when it creates this service.
    public KpiService(JdbcTemplate jdbc) {
        // Keep it so the methods below can use it.
        this.jdbc = jdbc;
    }

    // Returns every active KPI, ready for Superset (used by superset-provision and the metric sync).
    public List<Kpi> findActiveKpis() {
        // Now we will run the SQL and turn each row into a Kpi.
        return jdbc.query(ACTIVE_KPIS_SQL, (row, rowNumber) -> toKpi(row));
    }

    // Returns the Superset metric name of every KPI that exists (any status), e.g. "kpi_tch_01".
    // Superset formulas whose KPI is not in this list are removed at the next sync.
    public List<String> allMetricNames() {
        // Now we will read every KPI code and turn it into its metric name.
        List<String> names = jdbc.queryForList("SELECT code FROM kpi", String.class).stream()
                .map(KpiService::metricName).toList();
        // Now we will log them (detail level).
        log.debug("allMetricNames(): {}", names);
        // Hand them back.
        return names;
    }

    // Returns every KPI with its fields and scope, for the KPI registry screen.
    public List<KpiDetail> listAll() {
        // Now we will run the SQL and turn each row into a KpiDetail.
        return jdbc.query(ALL_KPIS_SQL, (row, rowNumber) -> toDetail(row));
    }

    // Creates a KPI (id null) or updates one, after checking the input. Returns the saved KPI.
    @Transactional
    public KpiDetail save(UUID id, KpiInput input) {
        // Now we will log what is being saved.
        log.info("{} KPI {} ({}), status {}, multiplier {}", id == null ? "Creating" : "Updating", input.code(),
                input.name(), input.status(), input.multiplier());
        // Check the input and stop with HTTP 400 if something is wrong.
        validate(input);
        // A new KPI gets a new id.
        UUID kpiId = id == null ? UUID.randomUUID() : id;
        // A new KPI is inserted ...
        if (id == null) {
            // Now we will insert the KPI row.
            jdbc.update("""
                    INSERT INTO kpi (id, code, name, category, description, multiplier, status,
                                     scope_school_types, scope_district_codes, scope_school_ids)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::text[], ?::text[], ?::uuid[])
                    """, kpiId, input.code(), input.name(), input.category(), input.description(),
                    input.multiplier(), input.status(), toArray(input.scopeSchoolTypes()),
                    toArray(input.scopeDistrictCodes()), toArray(input.scopeSchoolIds()));
        // ... an existing one is updated (its code stays, because Superset's metric is named after it).
        } else {
            // Now we will update the KPI row.
            int updated = jdbc.update("""
                    UPDATE kpi SET name = ?, category = ?, description = ?, multiplier = ?, status = ?,
                                   scope_school_types = ?::text[], scope_district_codes = ?::text[],
                                   scope_school_ids = ?::uuid[], updated_at = now()
                    WHERE id = ?
                    """, input.name(), input.category(), input.description(), input.multiplier(),
                    input.status(), toArray(input.scopeSchoolTypes()), toArray(input.scopeDistrictCodes()),
                    toArray(input.scopeSchoolIds()), kpiId);
            // No row updated means the id is unknown.
            if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "KPI not found.");
        }
        // Now we will log the scope saved.
        log.debug("save(): scope types {}, districts {}, schools {} (empty = everyone)", input.scopeSchoolTypes(),
                input.scopeDistrictCodes(), input.scopeSchoolIds());
        // Replace the KPI's field links: remove the old ones ...
        jdbc.update("DELETE FROM kpi_field WHERE kpi_id = ?", kpiId);
        // ... link the numerator ...
        jdbc.update("INSERT INTO kpi_field (kpi_id, field_id, part) VALUES (?, ?::uuid, 'NUMERATOR')",
                kpiId, input.numeratorFieldId());
        // ... and the denominator, if there is one.
        if (hasText(input.denominatorFieldId())) {
            // Link the denominator.
            jdbc.update("INSERT INTO kpi_field (kpi_id, field_id, part) VALUES (?, ?::uuid, 'DENOMINATOR')",
                    kpiId, input.denominatorFieldId());
        }
        // Now we will log the formula Superset will use.
        log.info("KPI {} saved (id {}): numerator field {}, denominator field {}", input.code(), kpiId,
                input.numeratorFieldId(), hasText(input.denominatorFieldId()) ? input.denominatorFieldId() : "none");
        // Return the saved KPI as the list shows it.
        return listAll().stream().filter(k -> k.id().equals(kpiId)).findFirst().orElseThrow();
    }

    // Checks a KPI from the editor; stops with HTTP 400 and a readable message if something is wrong.
    private void validate(KpiInput input) {
        // Now we will log the check (a helper inside this class, so the trace can't see it).
        log.debug("validate(): checking code, name, multiplier, status and fields of {}", input.code());
        // The code must look like KPI-TCH-01.
        if (input.code() == null || !input.code().matches("^[A-Z][A-Z0-9-]*$")) {
            // Explain the rule.
            throw badRequest("The code must use capital letters, digits and dashes, e.g. KPI-TCH-01.");
        }
        // The name is required.
        if (!hasText(input.name())) throw badRequest("Give the KPI a name.");
        // The multiplier must be positive.
        if (input.multiplier() <= 0) throw badRequest("The multiplier must be 1 or more (100 for a percentage).");
        // The status must be one we know.
        if (!STATUSES.contains(input.status())) throw badRequest("Unknown status: " + input.status());
        // The numerator is required and must be a number field.
        requireNumberField(input.numeratorFieldId(), "numerator");
        // The denominator is optional, but if given it must be a number field too.
        if (hasText(input.denominatorFieldId())) requireNumberField(input.denominatorFieldId(), "denominator");
    }

    // Stops with HTTP 400 unless the id is an existing NUMBER field.
    private void requireNumberField(String fieldId, String part) {
        // Now we will log the check.
        log.debug("requireNumberField(): {} field {} must be a NUMBER field", part, fieldId);
        // No field chosen.
        if (!hasText(fieldId)) throw badRequest("Choose the " + part + " field.");
        // Now we will count NUMBER fields with that id (0 or 1).
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM field WHERE id = ?::uuid AND field_type = 'NUMBER'", Integer.class, fieldId);
        // Not found, or not a number field.
        if (count == null || count == 0) throw badRequest("The " + part + " must be a number field.");
    }

    // Turns one row of ACTIVE_KPIS_SQL into a Kpi.
    private Kpi toKpi(ResultSet row) throws SQLException {
        // Read the KPI code, e.g. "KPI-TCH-01".
        String code = row.getString("code");
        // Read the column added up on top, e.g. "trained_teachers".
        String numerator = row.getString("numerator_code");
        // Read the column added up on the bottom, or null for a total.
        String denominator = row.getString("denominator_code");
        // Read the multiplier.
        int multiplier = row.getInt("multiplier");
        // Now we will put everything together into one Kpi.
        return new Kpi(row.getObject("id", UUID.class), code, row.getString("name"),
                row.getString("description"), numerator, denominator, multiplier, SLICE_COLUMNS,
                metricName(code), metricExpression(numerator, denominator, multiplier));
    }

    // Turns one row of ALL_KPIS_SQL into a KpiDetail.
    private KpiDetail toDetail(ResultSet row) throws SQLException {
        // Read the KPI code.
        String code = row.getString("code");
        // Read the numerator field code (may be missing on a half-made KPI).
        String numerator = row.getString("numerator_code");
        // Read the denominator field code, or null.
        String denominator = row.getString("denominator_code");
        // Read the multiplier.
        int multiplier = row.getInt("multiplier");
        // Now we will put everything together.
        return new KpiDetail(row.getObject("id", UUID.class), code, row.getString("name"),
                row.getString("category"), row.getString("description"), multiplier, row.getString("status"),
                text(row.getObject("numerator_field_id")), text(row.getObject("denominator_field_id")),
                numerator, denominator, fromArray(row.getArray("scope_school_types")),
                fromArray(row.getArray("scope_district_codes")), fromArray(row.getArray("scope_school_ids")),
                metricName(code), numerator == null ? null : metricExpression(numerator, denominator, multiplier));
    }

    // Builds the Superset formula: add up first, then divide. ".0" forces decimal division, and
    // NULLIF avoids dividing by zero. Without a denominator the KPI is a plain total.
    // Field codes are safe to paste into SQL: the database only accepts a-z, 0-9 and _ in them.
    public static String metricExpression(String numerator, String denominator, int multiplier) {
        // Now we will log the formula being built (a static helper, so the trace can't see it).
        log.debug("metricExpression(): {} / {} x {}", numerator, denominator == null ? "-" : denominator, multiplier);
        // A total: just add up the numerator (times the multiplier).
        if (denominator == null) return "SUM(" + numerator + ") * " + multiplier;
        // A ratio or percentage.
        return "SUM(" + numerator + ") * " + multiplier + ".0 / NULLIF(SUM(" + denominator + "), 0)";
    }

    // Builds a Superset-friendly metric name: "KPI-TCH-01" becomes "kpi_tch_01".
    public static String metricName(String code) {
        // Lower case, dashes to underscores.
        return code.toLowerCase(Locale.ROOT).replace("-", "_");
    }

    // Turns a list into an array the database driver understands (an empty list stays empty).
    private static String[] toArray(List<String> values) {
        // A missing list counts as empty.
        return values == null ? new String[0] : values.toArray(new String[0]);
    }

    // Turns a database array into a list of text.
    private static List<String> fromArray(Array array) throws SQLException {
        // The list we will return.
        List<String> values = new ArrayList<>();
        // A missing array gives an empty list.
        if (array == null) return values;
        // Add each element as text.
        for (Object value : (Object[]) array.getArray()) values.add(value.toString());
        // Return the list.
        return values;
    }

    // Turns a database value into text, keeping null as null.
    private static String text(Object value) {
        // Null stays null; anything else becomes text.
        return value == null ? null : value.toString();
    }

    // True when a string has something other than spaces in it.
    private static boolean hasText(String value) {
        // Not null and not blank.
        return value != null && !value.isBlank();
    }

    // Makes an HTTP 400 Bad Request error with a message for the frontend.
    private static ResponseStatusException badRequest(String message) {
        // Now we will log why the input is refused.
        log.debug("Refusing the KPI: {}", message);
        // Status 400 with the message.
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
