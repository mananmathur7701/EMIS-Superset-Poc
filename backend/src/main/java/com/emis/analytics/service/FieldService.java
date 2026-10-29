// Services: the rules of the app and the SQL that reads and writes the database.
package com.emis.analytics.service;

// Imports: JDBC rows, Java collections and Spring's SQL, HTTP error and transaction helpers.
import java.sql.ResultSet;
import java.sql.SQLException;
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
// Our own classes used here.
import com.emis.analytics.dto.request.FieldInput;
import com.emis.analytics.model.Field;

// The field library: everything a form can collect. KPIs are built from its number fields.
@Service
public class FieldService {

    // The field types a form question can have.
    private static final Set<String> TYPES = Set.of("NUMBER", "TEXT", "SELECT", "DATE");

    // SQL that lists every field with the KPIs and forms that use it.
    private static final String ALL_FIELDS_SQL = """
            -- Take the field's own columns ...
            SELECT f.*,
                   -- ... the codes of the KPIs built from it ...
                   ARRAY(SELECT DISTINCT k.code FROM kpi_field kf JOIN kpi k ON k.id = kf.kpi_id
                         WHERE kf.field_id = f.id ORDER BY k.code) AS used_by_kpis
            -- From the field table, in code order.
            FROM field f
            ORDER BY f.code
            """;

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(FieldService.class);

    // Spring's helper for running SQL.
    private final JdbcTemplate jdbc;

    // Spring passes the SQL helper in when it creates this service.
    public FieldService(JdbcTemplate jdbc) {
        // Keep it so the methods below can use it.
        this.jdbc = jdbc;
    }

    // Returns every field, for the field library screen and the KPI and form editors.
    public List<Field> listAll() {
        // Now we will run the SQL and turn each row into a Field.
        return jdbc.query(ALL_FIELDS_SQL, (row, rowNumber) -> toField(row));
    }

    // Creates a field (id null) or updates one. Returns the saved field. The caller rebuilds the
    // analytics view afterwards, because number fields are columns of it.
    @Transactional
    public Field save(UUID id, FieldInput input) {
        // Now we will log what is being saved.
        log.info("{} field {} (\"{}\", {})", id == null ? "Creating" : "Updating", input.code(), input.label(), input.fieldType());
        // Check the input and stop with HTTP 400 if something is wrong.
        validate(input);
        // A new field gets a new id.
        UUID fieldId = id == null ? UUID.randomUUID() : id;
        // A new field is inserted ...
        if (id == null) {
            // Now we will insert the field row.
            jdbc.update("""
                    INSERT INTO field (id, code, label, description, field_type, options, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, fieldId, input.code(), input.label(), input.description(), input.fieldType(),
                    input.options(), input.active());
        // ... an existing one is updated. Its code and type stay, because forms, answers and
        // the analytics view depend on them.
        } else {
            // Now we will update the field row.
            int updated = jdbc.update("""
                    UPDATE field SET label = ?, description = ?, options = ?, is_active = ? WHERE id = ?
                    """, input.label(), input.description(), input.options(), input.active(), fieldId);
            // No row updated means the id is unknown.
            if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Field not found.");
        }
        // Now we will log the result.
        log.info("Field {} saved (id {}, active {})", input.code(), fieldId, input.active());
        // Return the saved field as the list shows it.
        return listAll().stream().filter(f -> f.id().equals(fieldId)).findFirst().orElseThrow();
    }

    // Checks a field from the editor; stops with HTTP 400 and a readable message if something is wrong.
    private void validate(FieldInput input) {
        // Now we will log the check (a helper inside this class, so the call trace can't see it).
        log.debug("validate(): checking code {}, label, type {}", input.code(), input.fieldType());
        // The code becomes a database column, so it must be lowercase letters, digits and underscores.
        if (input.code() == null || !input.code().matches("^[a-z][a-z0-9_]*$")) {
            // Explain the rule.
            throw badRequest("The code must use lowercase letters, digits and underscores, e.g. total_pupils.");
        }
        // The analytics view already has these columns, so a field cannot use their names.
        if (AnalyticsViewService.FIXED_COLUMNS.contains(input.code())) {
            // Explain the rule.
            throw badRequest("'" + input.code() + "' is a reserved column name.");
        }
        // The label is required.
        if (input.label() == null || input.label().isBlank()) throw badRequest("Give the field a label.");
        // The type must be one we know.
        if (!TYPES.contains(input.fieldType())) throw badRequest("Unknown field type: " + input.fieldType());
        // A select field needs its choices.
        if (input.fieldType().equals("SELECT") && (input.options() == null || input.options().isBlank())) {
            // Explain the rule.
            throw badRequest("List the choices of a select field, separated by commas.");
        }
    }

    // Turns one row of ALL_FIELDS_SQL into a Field.
    private Field toField(ResultSet row) throws SQLException {
        // Read the KPI codes as a Java array.
        String[] kpis = (String[]) row.getArray("used_by_kpis").getArray();
        // Now we will put everything together.
        return new Field(row.getObject("id", UUID.class), row.getString("code"), row.getString("label"),
                row.getString("description"), row.getString("field_type"), row.getString("options"),
                row.getBoolean("is_active"), List.of(kpis));
    }

    // Makes an HTTP 400 Bad Request error with a message for the frontend.
    private static ResponseStatusException badRequest(String message) {
        // Now we will log why the field is refused.
        log.debug("Refusing the field: {}", message);
        // Status 400 with the message.
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
