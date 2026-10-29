// Services: the rules of the app and the SQL that reads and writes the database.
package com.emis.analytics.service;

// Imports: JDBC rows, Java collections, Jackson's JSON tree and Spring's SQL, HTTP error and
// transaction helpers.
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
// Our own classes used here.
import com.emis.analytics.dto.request.FormInput;
import com.emis.analytics.model.FormDetail;
import com.emis.analytics.model.FormRef;
import com.emis.analytics.model.FormSummary;

// Forms built in the Form.io builder, and the KPIs each form collects data for.
@Service
public class FormService {

    // The form statuses.
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED");

    // SQL that lists every form (without its definition) with its KPIs.
    private static final String ALL_FORMS_SQL = """
            -- Take the form's columns ...
            SELECT f.id, f.code, f.name, f.description, f.status, f.updated_at, f.schema_json,
                   -- ... plus the ids and codes of its KPIs.
                   ARRAY(SELECT k.id::text FROM form_kpi fk JOIN kpi k ON k.id = fk.kpi_id
                         WHERE fk.form_id = f.id ORDER BY k.code) AS kpi_ids,
                   ARRAY(SELECT k.code FROM form_kpi fk JOIN kpi k ON k.id = fk.kpi_id
                         WHERE fk.form_id = f.id ORDER BY k.code) AS kpi_codes
            FROM form f
            """;

    // SQL that finds the published forms a school must fill in: a form applies when it has no
    // active KPI at all, or when at least one of its active KPIs is reported by the school.
    private static final String FORMS_FOR_SCHOOL_SQL = """
            SELECT f.id, f.code, f.name, f.description
            FROM form f
            -- The school we are asking about.
            JOIN school s ON s.id = ?
            WHERE f.status = 'PUBLISHED'
              AND (NOT EXISTS (SELECT FROM form_kpi fk JOIN kpi k ON k.id = fk.kpi_id
                               WHERE fk.form_id = f.id AND k.status = 'ACTIVE')
                   OR EXISTS (SELECT FROM form_kpi fk JOIN kpi k ON k.id = fk.kpi_id
                              WHERE fk.form_id = f.id AND k.status = 'ACTIVE'
                                AND %s))
            ORDER BY f.name
            """.formatted(KpiService.KPI_COVERS_SCHOOL);

    // SQL that finds the field codes a school must NOT be asked: fields of the form's active KPIs
    // that the school does not report, minus fields that a KPI it does report also needs.
    private static final String HIDDEN_CODES_SQL = """
            SELECT fl.code
            FROM form_kpi fk JOIN kpi k ON k.id = fk.kpi_id
            JOIN kpi_field kf ON kf.kpi_id = k.id JOIN field fl ON fl.id = kf.field_id
            JOIN school s ON s.id = ?
            WHERE fk.form_id = ? AND k.status = 'ACTIVE' AND NOT (%1$s)
            EXCEPT
            SELECT fl.code
            FROM form_kpi fk JOIN kpi k ON k.id = fk.kpi_id
            JOIN kpi_field kf ON kf.kpi_id = k.id JOIN field fl ON fl.id = kf.field_id
            JOIN school s ON s.id = ?
            WHERE fk.form_id = ? AND k.status = 'ACTIVE' AND (%1$s)
            """.formatted(KpiService.KPI_COVERS_SCHOOL);

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(FormService.class);

    // Spring's helper for running SQL.
    private final JdbcTemplate jdbc;
    // Reads and writes JSON.
    private final JsonMapper json;

    // Spring passes the helpers in when it creates this service.
    public FormService(JdbcTemplate jdbc, JsonMapper json) {
        // Keep the SQL helper.
        this.jdbc = jdbc;
        // Keep the JSON helper.
        this.json = json;
    }

    // Returns every form with its KPIs and question keys, for the form list.
    public List<FormSummary> listAll() {
        // Now we will run the SQL and turn each row into a FormSummary.
        return jdbc.query(ALL_FORMS_SQL + " ORDER BY f.name", (row, rowNumber) -> toSummary(row));
    }

    // Returns one form with its Form.io definition, for the builder.
    public FormDetail get(UUID id) {
        // Now we will read the form's columns and its KPI ids.
        List<FormDetail> forms = jdbc.query(ALL_FORMS_SQL + " WHERE f.id = ?", (row, rowNumber) ->
                // Put the columns and the parsed definition together.
                new FormDetail(toSummary(row), json.readTree(row.getString("schema_json"))), id);
        // No row means the id is unknown.
        if (forms.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Form not found.");
        // Return the form.
        return forms.get(0);
    }

    // Creates a form (id null) or updates one, with its KPI links. Returns the saved form.
    @Transactional
    public FormDetail save(UUID id, FormInput input) {
        // Now we will log what is being saved.
        log.info("{} form {} (\"{}\"), status {}, linked to {} KPI(s)", id == null ? "Creating" : "Updating",
                input.code(), input.name(), input.status(), input.kpiIds() == null ? 0 : input.kpiIds().size());
        // Check the input and stop with HTTP 400 if something is wrong.
        validate(input);
        // A new form gets a new id.
        UUID formId = id == null ? UUID.randomUUID() : id;
        // The Form.io definition as JSON text.
        String schemaJson = json.writeValueAsString(input.schema());
        // A new form is inserted ...
        if (id == null) {
            // Now we will insert the form row.
            jdbc.update("""
                    INSERT INTO form (id, code, name, description, status, schema_json)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb)
                    """, formId, input.code(), input.name(), input.description(), input.status(), schemaJson);
        // ... an existing one is updated (its code stays).
        } else {
            // Now we will update the form row.
            int updated = jdbc.update("""
                    UPDATE form SET name = ?, description = ?, status = ?, schema_json = ?::jsonb,
                                    updated_at = now()
                    WHERE id = ?
                    """, input.name(), input.description(), input.status(), schemaJson, formId);
            // No row updated means the id is unknown.
            if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Form not found.");
        }
        // Replace the form's KPI links: remove the old ones ...
        jdbc.update("DELETE FROM form_kpi WHERE form_id = ?", formId);
        // ... and add each chosen KPI.
        for (String kpiId : input.kpiIds()) {
            // Link the KPI.
            jdbc.update("INSERT INTO form_kpi (form_id, kpi_id) VALUES (?, ?::uuid)", formId, kpiId);
        }
        // Now we will log the result (PUBLISHED forms appear for registrars from now on).
        log.info("Form {} saved (id {}) with {} question(s); {}", input.code(), formId,
                questionKeys(input.schema()).size(),
                "PUBLISHED".equals(input.status()) ? "registrars of covered schools can now fill it in" : "draft, not yet visible to registrars");
        // Return the saved form.
        return get(formId);
    }

    // Returns the published forms a school must fill in.
    public List<FormRef> formsForSchool(UUID schoolId) {
        // Now we will run the SQL for this school.
        return jdbc.query(FORMS_FOR_SCHOOL_SQL, (row, rowNumber) -> new FormRef(
                // Form id, code, name and description.
                row.getObject("id", UUID.class), row.getString("code"), row.getString("name"),
                row.getString("description")), schoolId);
    }

    // Returns the form's definition as one school sees it: questions that only KPIs the school
    // does not report would need are taken out.
    public JsonNode schemaForSchool(UUID formId, UUID schoolId) {
        // Read the full definition.
        JsonNode schema = get(formId).schema();
        // Now we will find the field codes to leave out for this school.
        Set<String> hidden = new HashSet<>(jdbc.queryForList(HIDDEN_CODES_SQL, String.class,
                schoolId, formId, schoolId, formId));
        // Now we will log which questions this school is not asked (KPI scope).
        log.debug("schemaForSchool(): school {} is not asked {}", schoolId, hidden.isEmpty() ? "anything extra" : hidden);
        // Take those questions out of the definition.
        removeQuestions(schema, hidden);
        // Return what is left.
        return schema;
    }

    // Returns the keys of every question in a Form.io definition (buttons and layout parts such
    // as panels have no answer, so they are skipped).
    public static List<String> questionKeys(JsonNode schema) {
        // The keys we will return.
        List<String> keys = new ArrayList<>();
        // Now we will walk the whole definition.
        collectKeys(schema, keys);
        // Return them.
        return keys;
    }

    // Walks the definition and collects question keys (a helper for questionKeys).
    private static void collectKeys(JsonNode node, List<String> keys) {
        // A question is an object with "input": true that is not a button.
        if (node.isObject() && node.path("input").asBoolean(false)
                && !"button".equals(node.path("type").asString(""))) {
            // Collect its key.
            keys.add(node.path("key").asString(""));
        }
        // Look inside every child value (panels, columns, tables and so on).
        for (JsonNode child : node) {
            // Walk the child too.
            collectKeys(child, keys);
        }
    }

    // Removes every question whose key is in the given set, wherever it sits in the definition.
    private static void removeQuestions(JsonNode node, Set<String> keys) {
        // Nothing to remove.
        if (keys.isEmpty()) return;
        // In a list, remove matching questions (going backwards, so removals do not shift the rest).
        if (node.isArray()) {
            // Treat it as a list we can change.
            ArrayNode list = (ArrayNode) node;
            // From the last item to the first.
            for (int i = list.size() - 1; i >= 0; i--) {
                // Read the item.
                JsonNode item = list.get(i);
                // It is a question we must hide ...
                if (item.isObject() && keys.contains(item.path("key").asString(""))) {
                    // ... so remove it.
                    list.remove(i);
                // Otherwise look inside it.
                } else {
                    // Walk the item.
                    removeQuestions(item, keys);
                }
            }
        // In an object, look inside every value.
        } else if (node.isObject()) {
            // Walk each value.
            for (JsonNode child : node) removeQuestions(child, keys);
        }
    }

    // Checks a form from the builder; stops with HTTP 400 and a readable message if something is wrong.
    private void validate(FormInput input) {
        // Now we will log the check (a helper inside this class, so the call trace can't see it).
        log.debug("validate(): checking code {}, name, status {}, definition and question keys", input.code(), input.status());
        // The code must look like CENSUS.
        if (input.code() == null || !input.code().matches("^[A-Z][A-Z0-9_-]*$")) {
            // Explain the rule.
            throw badRequest("The code must use capital letters, digits, dashes and underscores, e.g. CENSUS.");
        }
        // The name is required.
        if (input.name() == null || input.name().isBlank()) throw badRequest("Give the form a name.");
        // The status must be one we know.
        if (!STATUSES.contains(input.status())) throw badRequest("Unknown status: " + input.status());
        // The definition must be a Form.io form with a list of components.
        if (input.schema() == null || !input.schema().path("components").isArray()) {
            // Explain the rule.
            throw badRequest("The form definition is missing its components.");
        }
        // A missing KPI list counts as empty.
        if (input.kpiIds() == null) throw badRequest("Send the KPI list (it may be empty).");
        // A published form must only ask questions whose answers we can store: every question
        // key must be a field code from the field library.
        if (input.status().equals("PUBLISHED")) {
            // Now we will read every field code.
            Set<String> fieldCodes = new HashSet<>(jdbc.queryForList("SELECT code FROM field", String.class));
            // The question keys that are not field codes.
            List<String> unknown = questionKeys(input.schema()).stream()
                    .filter(key -> !fieldCodes.contains(key)).toList();
            // Refuse to publish if there are any.
            if (!unknown.isEmpty()) {
                // Name them, so the analyst can fix each question's key (or add the field first).
                throw badRequest("These question keys are not fields in the field library: "
                        + String.join(", ", unknown) + ". Set each question's Property Name to a field code.");
            }
        }
    }

    // Turns one row of ALL_FORMS_SQL into a FormSummary.
    private FormSummary toSummary(ResultSet row) throws SQLException {
        // Read the KPI ids and codes as Java arrays.
        String[] kpiIds = (String[]) row.getArray("kpi_ids").getArray();
        // The codes.
        String[] kpiCodes = (String[]) row.getArray("kpi_codes").getArray();
        // Count the questions in the definition.
        int questions = questionKeys(json.readTree(row.getString("schema_json"))).size();
        // Now we will put everything together.
        return new FormSummary(row.getObject("id", UUID.class), row.getString("code"), row.getString("name"),
                row.getString("description"), row.getString("status"), List.of(kpiIds), List.of(kpiCodes),
                questions, row.getObject("updated_at", java.time.OffsetDateTime.class).toString());
    }

    // Makes an HTTP 400 Bad Request error with a message for the frontend.
    private static ResponseStatusException badRequest(String message) {
        // Now we will log why the form is refused.
        log.debug("Refusing the form: {}", message);
        // Status 400 with the message.
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
