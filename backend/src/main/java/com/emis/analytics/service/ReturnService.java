// Services: the rules of the app and the SQL that reads and writes the database.
package com.emis.analytics.service;

// Imports: numbers, Java collections and Spring's settings, SQL, HTTP error and transaction helpers.
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
// Our own classes used here.
import com.emis.analytics.model.FormRef;
import com.emis.analytics.model.Place;
import com.emis.analytics.model.ReturnDetail;
import com.emis.analytics.model.ReturnEvent;
import com.emis.analytics.model.ReturnSummary;
import com.emis.analytics.model.Viewer;

// A school's answers to a form ("return") and its review flow:
// DRAFT (registrar) -> WITH_PRINCIPAL -> WITH_DEO -> WITH_CPU -> FROZEN (counts in dashboards).
// Approving moves a return one step forward; sending it back moves it one step back.
@Service
public class ReturnService {

    // The review flow in order.
    private static final List<String> FLOW = List.of("DRAFT", "WITH_PRINCIPAL", "WITH_DEO", "WITH_CPU", "FROZEN");

    // SQL that reads returns with their form and school. Callers add a WHERE clause.
    private static final String RETURNS_SQL = """
            SELECT r.id, r.form_id, f.name AS form_name, r.school_id, s.name AS school_name,
                   r.district_code, r.academic_year, r.status, r.updated_at,
                   -- The comment of the latest "sent back", if any.
                   (SELECT e.comment FROM form_return_event e WHERE e.return_id = r.id
                    AND e.action = 'RETURNED' ORDER BY e.created_at DESC LIMIT 1) AS last_return_comment
            FROM form_return r
            JOIN form f ON f.id = r.form_id
            JOIN school s ON s.id = r.school_id
            """;

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(ReturnService.class);

    // Spring's helper for running SQL.
    private final JdbcTemplate jdbc;
    // Reads forms.
    private final FormService formService;
    // The academic year registrars report for now (emis.reporting-year).
    private final String reportingYear;

    // Spring passes the helpers and the setting in when it creates this service.
    public ReturnService(JdbcTemplate jdbc, FormService formService,
                         @Value("${emis.reporting-year}") String reportingYear) {
        // Keep the SQL helper.
        this.jdbc = jdbc;
        // Keep the form service.
        this.formService = formService;
        // Keep the reporting year.
        this.reportingYear = reportingYear;
    }

    // The registrar's to-do list: each form each of their schools must fill in this year, with
    // its status ("NOT_STARTED" when there is no return yet).
    public List<ReturnSummary> myForms(Viewer viewer) {
        // The list we will return.
        List<ReturnSummary> items = new ArrayList<>();
        // Go through each school where the user is registrar.
        for (Place place : viewer.places()) {
            // Skip other roles.
            if (!place.role().equals("REGISTRAR")) continue;
            // The school's id.
            UUID schoolId = UUID.fromString(place.schoolId());
            // Go through each form the school must fill in.
            for (FormRef form : formService.formsForSchool(schoolId)) {
                // Look for this year's return.
                ReturnSummary existing = findReturn(form.id(), schoolId, reportingYear);
                // Use it, or describe a return that has not been started.
                items.add(existing != null ? existing : new ReturnSummary(null, form.id(), form.name(),
                        schoolId, place.schoolName(), place.districtCode(), reportingYear,
                        "NOT_STARTED", null, null, true));
            }
        }
        // Now we will log the result.
        log.debug("myForms(): {} form(s) for {} this year ({})", items.size(), viewer.username(), reportingYear);
        // Return the list.
        return items;
    }

    // The reviewer's list for this year: every return they may see, with the ones waiting for
    // them marked "actionable" and listed first.
    public List<ReturnSummary> reviewList(Viewer viewer) {
        // Start the SQL: this year's returns ...
        String sql = RETURNS_SQL + " WHERE r.academic_year = ?";
        // ... and the values for its question marks.
        List<Object> values = new ArrayList<>(List.of(reportingYear));
        // A CPU analyst sees every school; others only their schools and districts.
        if (!viewer.roles().contains("CPU_ANALYST")) {
            // Schools where they are principal, or districts where they are DEO.
            sql += " AND (r.school_id::text = ANY (?) OR r.district_code = ANY (?))";
            // The school ids.
            values.add(viewer.schoolsFor("PRINCIPAL").toArray(new String[0]));
            // The district codes.
            values.add(viewer.deoDistricts().toArray(new String[0]));
        }
        // Now we will run the SQL, in school order.
        List<ReturnSummary> items = jdbc.query(sql + " ORDER BY s.name", (row, n) -> toSummary(row, viewer),
                values.toArray());
        // Now we will log the result.
        log.debug("reviewList(): {} return(s) visible to {}, {} waiting for them", items.size(), viewer.username(),
                items.stream().filter(ReturnSummary::actionable).count());
        // Put the returns waiting for this user first (the sort keeps the school order otherwise).
        return items.stream().sorted((a, b) -> Boolean.compare(b.actionable(), a.actionable())).toList();
    }

    // Opens a return for the registrar: the school's version of the form plus the saved answers.
    // If nothing is saved yet the answers are empty.
    public ReturnDetail openForRegistrar(Viewer viewer, UUID formId, UUID schoolId) {
        // Only the school's registrar may open it this way.
        requireRegistrar(viewer, schoolId);
        // Look for this year's return.
        ReturnSummary summary = findReturn(formId, schoolId, reportingYear);
        // None yet: an empty, editable form.
        if (summary == null) {
            // Check the form applies to this school.
            FormRef form = requireFormForSchool(formId, schoolId);
            // The school's name and district, from the user's place.
            Place place = viewer.places().stream()
                    .filter(p -> schoolId.toString().equals(p.schoolId())).findFirst().orElseThrow();
            // Describe the not-yet-started return.
            summary = new ReturnSummary(null, formId, form.name(), schoolId, place.schoolName(),
                    place.districtCode(), reportingYear, "NOT_STARTED", null, null, true);
        }
        // Build the full detail.
        return detail(viewer, summary);
    }

    // Opens a return by id for a reviewer (read-only, with Approve / Send back if it is their step).
    public ReturnDetail openById(Viewer viewer, UUID returnId) {
        // Read the return.
        ReturnSummary summary = requireReturn(returnId, viewer);
        // Only people who may see the school's returns may open it.
        if (!canSee(viewer, summary)) {
            // Now we will log the refusal.
            log.debug("openById(): {} may not see {} ({})", viewer.username(), summary.schoolName(), summary.districtCode());
            // Send HTTP 403 Forbidden.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This return is outside your schools.");
        }
        // Build the full detail.
        return detail(viewer, summary);
    }

    // Saves the registrar's answers; with submit=true also sends the return to the principal.
    @Transactional
    public ReturnDetail save(Viewer viewer, UUID formId, UUID schoolId,
                             Map<String, Object> data, boolean submit) {
        // Now we will log what is being saved.
        log.info("{} return: form {}, school {}, {} answer(s) sent", submit ? "Submitting" : "Saving draft of", formId,
                schoolId, data.size());
        // Only the school's registrar may save.
        requireRegistrar(viewer, schoolId);
        // The form must apply to this school.
        requireFormForSchool(formId, schoolId);
        // Look for this year's return.
        ReturnSummary existing = findReturn(formId, schoolId, reportingYear);
        // The return's id: the existing one, or a new one.
        UUID returnId = existing != null ? existing.id() : UUID.randomUUID();
        // A new return is created as a draft, copying the school's district, type and level.
        if (existing == null) {
            // Now we will log that this is the first save.
            log.info("First save: creating return {} as DRAFT for {}", returnId, reportingYear);
            // Now we will insert the return.
            jdbc.update("""
                    INSERT INTO form_return (id, form_id, school_id, academic_year, status,
                                             district_code, school_type, school_level)
                    SELECT ?, ?, s.id, ?, 'DRAFT', s.district_code, s.type, s.level FROM school s WHERE s.id = ?
                    """, returnId, formId, reportingYear, schoolId);
        // An existing return can only change while it is a draft.
        } else if (!existing.status().equals("DRAFT")) {
            // Now we will log the refusal.
            log.debug("save(): return {} is {}, not DRAFT, so it can't change", returnId, existing.status());
            // Send HTTP 409 Conflict.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This return was already submitted and is " + existing.status() + ".");
        }

        // The questions this school is asked: only their answers are stored.
        List<String> keys = FormService.questionKeys(formService.schemaForSchool(formId, schoolId));
        // Now we will read the library fields behind those questions: code -> (id, type).
        Map<String, String[]> fields = new LinkedHashMap<>();
        // Read each field.
        jdbc.query("SELECT id::text, code, field_type FROM field", row -> {
            // Keep it if the form asks it.
            if (keys.contains(row.getString("code"))) {
                // Remember its id and type.
                fields.put(row.getString("code"), new String[] {row.getString("id"), row.getString("field_type")});
            }
        });
        // Go through each question's field.
        for (Map.Entry<String, String[]> field : fields.entrySet()) {
            // The answer the registrar gave (may be missing).
            Object answer = data.get(field.getKey());
            // Remove the old answer first.
            jdbc.update("DELETE FROM form_return_value WHERE return_id = ? AND field_id = ?::uuid",
                    returnId, field.getValue()[0]);
            // An empty answer stays removed.
            if (answer == null || answer.toString().isBlank()) continue;
            // A number field stores a number ...
            if (field.getValue()[1].equals("NUMBER")) {
                // Now we will store it.
                jdbc.update("INSERT INTO form_return_value (return_id, field_id, number_value) VALUES (?, ?::uuid, ?)",
                        returnId, field.getValue()[0], toNumber(field.getKey(), answer));
            // ... other fields store text.
            } else {
                // Now we will store it.
                jdbc.update("INSERT INTO form_return_value (return_id, field_id, text_value) VALUES (?, ?::uuid, ?)",
                        returnId, field.getValue()[0], answer.toString());
            }
        }

        // Submitting moves the draft to the principal ...
        if (submit) {
            // Now we will check every number question is answered.
            for (Map.Entry<String, String[]> field : fields.entrySet()) {
                // A number field without an answer ...
                Object answer = data.get(field.getKey());
                // ... cannot be submitted.
                if (field.getValue()[1].equals("NUMBER") && (answer == null || answer.toString().isBlank())) {
                    // Send HTTP 400 naming the question.
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer '" + field.getKey() + "' before submitting.");
                }
            }
            // Move the return on and record it.
            move(viewer, returnId, "DRAFT", "WITH_PRINCIPAL", "SUBMITTED", null);
        // ... saving keeps it as a draft (repeated saves are recorded once).
        } else {
            // Mark the return as changed.
            jdbc.update("UPDATE form_return SET updated_at = now() WHERE id = ?", returnId);
            // Now we will read the last thing that happened to the return.
            List<String> last = jdbc.queryForList("""
                    SELECT action FROM form_return_event WHERE return_id = ? ORDER BY created_at DESC LIMIT 1
                    """, String.class, returnId);
            // Record "saved" unless the last event already was.
            if (last.isEmpty() || !last.get(0).equals("SAVED")) {
                // Record it.
                addEvent(viewer, returnId, "SAVED", "DRAFT", "DRAFT", null);
            }
        }
        // Now we will log which answers were stored.
        log.debug("save(): stored answers for {}", fields.keySet());
        // Return the saved return.
        return detail(viewer, findReturn(formId, schoolId, reportingYear));
    }

    // Approves a return (one step forward) or sends it back (one step back, comment required).
    @Transactional
    public ReturnDetail review(Viewer viewer, UUID returnId, String action, String comment) {
        // Read the return.
        ReturnSummary summary = requireReturn(returnId, viewer);
        // Now we will log the decision being made.
        log.info("{} on return {} ({} / {}), now {}, comment: {}", action, returnId, summary.schoolName(),
                summary.formName(), summary.status(), comment == null || comment.isBlank() ? "none" : "\"" + comment + "\"");
        // Only the person at the return's current step may act on it.
        if (!summary.actionable()) {
            // Now we will log the refusal.
            log.debug("review(): return is {}, which is not {}'s step", summary.status(), viewer.username());
            // Send HTTP 403 Forbidden.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This return is not waiting for you.");
        }
        // Where the return is now in the flow.
        int step = FLOW.indexOf(summary.status());
        // Approve: move one step forward.
        if (action.equals("APPROVE")) {
            // Move it and record it.
            move(viewer, returnId, summary.status(), FLOW.get(step + 1), "APPROVED", comment);
        // Send back: move one step back, with a reason.
        } else if (action.equals("RETURN")) {
            // The reason is required.
            if (comment == null || comment.isBlank()) {
                // Send HTTP 400.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say why you are sending it back.");
            }
            // Move it and record it.
            move(viewer, returnId, summary.status(), FLOW.get(step - 1), "RETURNED", comment);
        // Anything else is a mistake.
        } else {
            // Send HTTP 400.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown action: " + action);
        }
        // Return the updated return.
        return detail(viewer, requireReturn(returnId, viewer));
    }

    // Changes a return's status (only if it is still in the expected status) and records why.
    private void move(Viewer viewer, UUID returnId, String from, String to, String action,
                      String comment) {
        // Now we will log the move (a helper inside this class, so the call trace can't see it).
        log.info("move(): return {} {} → {} ({} by {})", returnId, from, to, action, viewer.username());
        // Now we will change the status.
        int updated = jdbc.update("UPDATE form_return SET status = ?, updated_at = now() WHERE id = ? AND status = ?",
                to, returnId, from);
        // Someone else moved it in the meantime.
        if (updated == 0) {
            // Send HTTP 409 Conflict.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Someone else changed this return. Reload it.");
        }
        // A frozen return counts in dashboards from now on.
        if (to.equals("FROZEN")) log.info("Return {} is FROZEN: it now counts in analytics.census_frozen and the dashboards", returnId);
        // Record it in the history.
        addEvent(viewer, returnId, action, from, to, comment);
    }

    // Adds one line to a return's history.
    private void addEvent(Viewer viewer, UUID returnId, String action, String from, String to,
                          String comment) {
        // Now we will log the history line.
        log.debug("addEvent(): history of {}: {} {} → {} by {}", returnId, action, from, to, viewer.username());
        // Now we will insert the event.
        jdbc.update("""
                INSERT INTO form_return_event (id, return_id, person_id, action, from_status, to_status, comment)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), returnId, viewer.personId(), action, from, to,
                comment == null || comment.isBlank() ? null : comment.trim());
    }

    // Builds the full detail of a return: the school's form, the answers and the history.
    private ReturnDetail detail(Viewer viewer, ReturnSummary summary) {
        // The form as this school sees it.
        JsonNode schema = formService.schemaForSchool(summary.formId(), summary.schoolId());
        // The answers, as Form.io expects them: field code -> value.
        Map<String, Object> data = new LinkedHashMap<>();
        // The history, oldest first.
        List<ReturnEvent> history = new ArrayList<>();
        // A started return has answers and history.
        if (summary.id() != null) {
            // Now we will read the answers.
            jdbc.query("""
                    SELECT f.code, v.number_value, v.text_value FROM form_return_value v
                    JOIN field f ON f.id = v.field_id WHERE v.return_id = ?
                    """, row -> {
                // The number, if it is a number answer.
                BigDecimal number = row.getBigDecimal("number_value");
                // Keep the number (without trailing zeros, written plainly: 250, not 2.5E+2) or the text.
                data.put(row.getString("code"), number != null
                        ? new BigDecimal(number.stripTrailingZeros().toPlainString()) : row.getString("text_value"));
            }, summary.id());
            // Now we will read the history.
            history = jdbc.query("""
                    SELECT e.action, e.from_status, e.to_status, e.comment, e.created_at, p.full_name
                    FROM form_return_event e LEFT JOIN person p ON p.id = e.person_id
                    WHERE e.return_id = ? ORDER BY e.created_at
                    """, (row, n) -> new ReturnEvent(row.getString("action"), row.getString("from_status"),
                    row.getString("to_status"), row.getString("comment"), row.getString("full_name"),
                    row.getObject("created_at", java.time.OffsetDateTime.class).toString()), summary.id());
        }
        // The registrar may edit only a draft (or a return not started yet) of their own school.
        boolean canEdit = viewer.schoolsFor("REGISTRAR").contains(summary.schoolId().toString())
                && (summary.status().equals("DRAFT") || summary.status().equals("NOT_STARTED"));
        // Now we will log what the screen gets.
        log.debug("detail(): return {} is {}, {} answer(s), {} history line(s), editable {}", summary.id(),
                summary.status(), data.size(), history.size(), canEdit);
        // Now we will put everything together.
        return new ReturnDetail(summary, schema, data, history, canEdit);
    }

    // Finds the return of a form, school and year, or null if there is none.
    private ReturnSummary findReturn(UUID formId, UUID schoolId, String year) {
        // Now we will run the SQL. A registrar's own return is always "actionable" while a draft.
        List<ReturnSummary> rows = jdbc.query(RETURNS_SQL + " WHERE r.form_id = ? AND r.school_id = ? AND r.academic_year = ?",
                (row, n) -> toSummary(row, null), formId, schoolId, year);
        // Return the first row, or null.
        return rows.isEmpty() ? null : rows.get(0);
    }

    // Reads a return by id for this user, or stops with HTTP 404.
    private ReturnSummary requireReturn(UUID returnId, Viewer viewer) {
        // Now we will run the SQL.
        List<ReturnSummary> rows = jdbc.query(RETURNS_SQL + " WHERE r.id = ?", (row, n) -> toSummary(row, viewer), returnId);
        // None found.
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Return not found.");
        // Return it.
        return rows.get(0);
    }

    // Turns one row of RETURNS_SQL into a ReturnSummary. "actionable" says whether the given
    // reviewer is at the return's current step (for a registrar's list: whether it is a draft).
    private ReturnSummary toSummary(java.sql.ResultSet row, Viewer reviewer) throws java.sql.SQLException {
        // Read the status, school and district.
        String status = row.getString("status");
        // The school id.
        UUID schoolId = row.getObject("school_id", UUID.class);
        // The district (as it was when the return was started).
        String district = row.getString("district_code");
        // Work out whether the return is waiting for this person.
        boolean actionable = reviewer == null ? status.equals("DRAFT") : switch (status) {
            // At the principal's step: the school's principal.
            case "WITH_PRINCIPAL" -> reviewer.schoolsFor("PRINCIPAL").contains(schoolId.toString());
            // At the DEO's step: the district's DEO.
            case "WITH_DEO" -> reviewer.deoDistricts().contains(district);
            // At the CPU step: any CPU analyst.
            case "WITH_CPU" -> reviewer.roles().contains("CPU_ANALYST");
            // Drafts and frozen returns wait for no reviewer.
            default -> false;
        };
        // Now we will put everything together.
        return new ReturnSummary(row.getObject("id", UUID.class), row.getObject("form_id", UUID.class),
                row.getString("form_name"), schoolId, row.getString("school_name"), district,
                row.getString("academic_year"), status,
                row.getObject("updated_at", java.time.OffsetDateTime.class).toString(),
                row.getString("last_return_comment"), actionable);
    }

    // True when the user may see this return: CPU analysts all, DEOs their district, principals
    // and registrars their school.
    private static boolean canSee(Viewer viewer, ReturnSummary summary) {
        // The school id as text.
        String school = summary.schoolId().toString();
        // Check each kind of access.
        return viewer.roles().contains("CPU_ANALYST")
                || viewer.deoDistricts().contains(summary.districtCode())
                || viewer.schoolsFor("PRINCIPAL").contains(school)
                || viewer.schoolsFor("REGISTRAR").contains(school);
    }

    // Stops with HTTP 403 unless the user is registrar at this school.
    private static void requireRegistrar(Viewer viewer, UUID schoolId) {
        // Not their school.
        if (!viewer.schoolsFor("REGISTRAR").contains(schoolId.toString())) {
            // Now we will log the refusal.
            log.debug("requireRegistrar(): {} is not registrar of school {}", viewer.username(), schoolId);
            // Send HTTP 403 Forbidden.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the registrar of this school.");
        }
    }

    // Returns the form if the school must fill it in, or stops with HTTP 404.
    private FormRef requireFormForSchool(UUID formId, UUID schoolId) {
        // Look for it among the school's forms.
        return formService.formsForSchool(schoolId).stream().filter(f -> f.id().equals(formId)).findFirst()
                // Not there: unknown, unpublished, or not for this school.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This form is not open for your school."));
    }

    // Turns an answer into a number, or stops with HTTP 400 naming the question.
    private static BigDecimal toNumber(String code, Object answer) {
        // Numbers come as text or as JSON numbers; both turn into text first.
        try {
            // Now we will read the number.
            BigDecimal number = new BigDecimal(answer.toString().trim());
            // Census figures are never negative.
            if (number.signum() < 0) throw new NumberFormatException();
            // Return it.
            return number;
        // Not a number, or negative.
        } catch (NumberFormatException e) {
            // Send HTTP 400.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'" + code + "' must be a number of 0 or more.");
        }
    }
}
