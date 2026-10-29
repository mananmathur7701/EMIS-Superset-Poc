// Services: the rules of the app and the SQL that reads and writes the database.
package com.emis.analytics.service;

// Imports: Java collections, Spring's SQL helper and HTTP error helpers.
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
// Our own classes used here.
import com.emis.analytics.model.Place;
import com.emis.analytics.model.Viewer;

// Reads accounts and roles: who a user is, what they may do, and which census rows they may see.
@Service
public class AccessService {

    // May open published dashboards.
    public static final String DASHBOARDS = "DASHBOARDS";
    // May use the Superset chart builder.
    public static final String BUILD = "BUILD";
    // May define KPIs, fields, forms and dashboard access.
    public static final String MANAGE_KPIS = "MANAGE_KPIS";
    // May fill in and submit forms for their school.
    public static final String FILL_FORMS = "FILL_FORMS";
    // May review returns at their step of the flow.
    public static final String REVIEW = "REVIEW";

    // SQL that finds the roles a username holds today. Ended and future roles are left out.
    private static final String CURRENT_ROLES_SQL = """
            -- Take the person, each role, and the place it is held at.
            SELECT p.id AS person_id, p.full_name, r.role, r.district_code, r.school_id,
                   s.name AS school_name, s.district_code AS school_district
            -- Start from the login account ...
            FROM account a
            -- ... go to the person who owns it ...
            JOIN person p ON p.id = a.person_id
            -- ... and to every role that person holds.
            JOIN school_role r ON r.person_id = p.id
            -- Look up the school for school roles (empty for other roles).
            LEFT JOIN school s ON s.id = r.school_id
            -- Match the username we were given.
            WHERE a.username = ?
              -- The account and the person must both be active.
              AND a.is_active AND p.is_active
              -- The role must have started already ...
              AND r.start_date <= CURRENT_DATE
              -- ... and must not have ended yet (an empty end date means it is current).
              AND (r.end_date IS NULL OR r.end_date >= CURRENT_DATE)
            """;

    // SQL that finds the stored password hash of an active account.
    private static final String PASSWORD_HASH_SQL = """
            -- Take the BCrypt hash ...
            SELECT a.password_hash
            -- ... of the account ...
            FROM account a
            -- ... whose person ...
            JOIN person p ON p.id = a.person_id
            -- ... matches the username, with both account and person active.
            WHERE a.username = ? AND a.is_active AND p.is_active
            """;

    // SQL that lists every active username, ordered by the person's name.
    private static final String ACTIVE_USERNAMES_SQL = """
            -- Take the username ...
            SELECT a.username
            -- ... of every account ...
            FROM account a
            -- ... and its person ...
            JOIN person p ON p.id = a.person_id
            -- ... where both are active ...
            WHERE a.is_active AND p.is_active
            -- ... sorted by name.
            ORDER BY p.full_name
            """;

    // Spring's helper for running SQL.
    private final JdbcTemplate jdbc;

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(AccessService.class);

    // Spring passes the JdbcTemplate in when it creates this service.
    public AccessService(JdbcTemplate jdbc) {
        // Keep it so the methods below can use it.
        this.jdbc = jdbc;
    }

    // Returns the stored password hash for a username, or null if there is no active account.
    public String findPasswordHash(String username) {
        // Now we will look up the hash (the list is empty if the account does not exist).
        List<String> hashes = jdbc.queryForList(PASSWORD_HASH_SQL, String.class, username);
        // Now we will log whether an active account was found (never the hash itself).
        log.debug("Active account for {}: {}", username, hashes.isEmpty() ? "none" : "found");
        // Return the hash, or null when nothing was found.
        return hashes.isEmpty() ? null : hashes.get(0);
    }

    // Returns the user, or stops the request with HTTP 403 if they have no current role.
    public Viewer requireViewer(String username) {
        // Now we will build the user from the database.
        Viewer viewer = findViewer(username);
        // No current role (or no active account) means no access.
        if (viewer == null) {
            // Now we will log why access is refused.
            log.debug("{} has no active account or no current role", username);
            // Send HTTP 403 Forbidden to the frontend with this message.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "User '" + username + "' has no active account or no current role");
        }
        // Return the user.
        return viewer;
    }

    // Stops the request with HTTP 403 unless the user has the given capability.
    public static void requireCapability(Viewer viewer, String capability) {
        // The user's capabilities do not include this one ...
        if (!viewer.capabilities().contains(capability)) {
            // ... log it (a static helper, so the call trace can't see it) ...
            log.debug("requireCapability(): {} lacks {} (has {})", viewer.username(), capability, viewer.capabilities());
            // ... so send HTTP 403 Forbidden.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your role does not allow this.");
        }
        // Now we will log that the check passed.
        log.debug("requireCapability(): {} may {}", viewer.username(), capability);
    }

    // Returns every active user who holds a current role (for the demo "switch account" list).
    public List<Viewer> allViewers() {
        // Now we will read every active username.
        List<String> usernames = jdbc.queryForList(ACTIVE_USERNAMES_SQL, String.class);
        // The users we will return.
        List<Viewer> viewers = new ArrayList<>();
        // Go through every username.
        for (String username : usernames) {
            // Build that user.
            Viewer viewer = findViewer(username);
            // Keep them only if they hold a current role.
            if (viewer != null) {
                // Add them to the list.
                viewers.add(viewer);
            }
        }
        // Return the list.
        return viewers;
    }

    // Builds the user: roles, places, capabilities and row-level security (RLS) clause.
    // Returns null if they hold no current role.
    public Viewer findViewer(String username) {
        // Now we will load the user's current roles from the database.
        List<Map<String, Object>> rows = jdbc.queryForList(CURRENT_ROLES_SQL, username);
        // No rows means no current role (or no active account).
        if (rows.isEmpty()) {
            // Tell the caller there is no such user.
            return null;
        }

        // Every row carries the same person, so read the id and name from the first one.
        UUID personId = (UUID) rows.get(0).get("person_id");
        // The person's name.
        String fullName = (String) rows.get(0).get("full_name");
        // The role names, e.g. "DEO".
        List<String> roleNames = new ArrayList<>();
        // Each role with the place it is held at.
        List<Place> places = new ArrayList<>();
        // Human-readable places, e.g. "District D03", shown in the frontend header.
        List<String> scopes = new ArrayList<>();
        // SQL conditions, one per place; a Set drops duplicates.
        Set<String> conditions = new LinkedHashSet<>();
        // What the user may do; a Set drops duplicates.
        Set<String> capabilities = new LinkedHashSet<>();
        // Becomes true if any role sees the whole country (Minister or CPU analyst).
        boolean national = false;

        // Now we will go through each role.
        for (Map<String, Object> row : rows) {
            // Read the role name, e.g. "DEO".
            String role = (String) row.get("role");
            // Remember the role name.
            roleNames.add(role);
            // The school id as text (empty for national and district roles).
            String schoolId = row.get("school_id") == null ? null : row.get("school_id").toString();
            // The district: the DEO's own, or the school's.
            String district = row.get("district_code") != null
                    ? (String) row.get("district_code") : (String) row.get("school_district");
            // Remember the role with its place.
            places.add(new Place(role, schoolId, (String) row.get("school_name"), district));

            // The Minister sees every row and opens dashboards.
            if (role.equals("MINISTER")) {
                // National user.
                national = true;
                // Describe the place.
                scopes.add("National");
                // Dashboards only.
                capabilities.add(DASHBOARDS);
            // A CPU analyst sees every row and does everything.
            } else if (role.equals("CPU_ANALYST")) {
                // National user.
                national = true;
                // Describe the place.
                scopes.add("National");
                // Dashboards, builder, KPI set-up and the final review.
                capabilities.addAll(List.of(DASHBOARDS, BUILD, MANAGE_KPIS, REVIEW));
            // A DEO sees one district.
            } else if (role.equals("DEO")) {
                // Describe the place.
                scopes.add("District " + district);
                // Only rows of that district.
                conditions.add("district_code = " + sqlText(district));
                // Dashboards and the district review.
                capabilities.addAll(List.of(DASHBOARDS, REVIEW));
            // Every other role (principal, registrar, teacher, student) belongs to one school.
            } else {
                // Describe the place by the school's name.
                scopes.add((String) row.get("school_name"));
                // Only rows of that school, matched by its id.
                conditions.add("school_id = " + sqlText(schoolId));
                // A principal opens dashboards and reviews; a registrar fills in forms.
                if (role.equals("PRINCIPAL")) capabilities.addAll(List.of(DASHBOARDS, REVIEW));
                // A registrar fills in forms (teachers and students get nothing).
                if (role.equals("REGISTRAR")) capabilities.add(FILL_FORMS);
            }
        }

        // National users get no clause (null); everyone else gets their places joined with OR.
        String rlsClause = national ? null : joinWithOr(conditions);
        // Now we will log who this is and what they may do.
        log.debug("findViewer(): {} = {} {}, places {}, capabilities {}, row filter {}", username, fullName, roleNames,
                scopes, capabilities, rlsClause == null ? "none (national)" : rlsClause);
        // Now we will return the user.
        return new Viewer(personId, username, fullName, roleNames, places, scopes, rlsClause,
                new ArrayList<>(capabilities));
    }

    // Joins conditions with OR: "a" stays "a"; "a" and "b" become "(a) OR (b)".
    private static String joinWithOr(Set<String> conditions) {
        // One condition needs no brackets.
        if (conditions.size() == 1) {
            // Return it as it is.
            return conditions.iterator().next();
        }
        // Wrap each condition in brackets and put OR between them.
        return "(" + String.join(") OR (", conditions) + ")";
    }

    // Turns a value into a safe SQL text literal: D03 becomes 'D03', and quotes are doubled.
    private static String sqlText(String value) {
        // Double any single quote, then wrap the value in single quotes.
        return "'" + value.replace("'", "''") + "'";
    }
}
