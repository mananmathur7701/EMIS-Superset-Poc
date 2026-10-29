// Model: data the backend reads from the database and sends to the frontend as JSON.
package com.emis.analytics.model;

// Imports: the types this record uses.
import java.util.List;
import java.util.UUID;

// A user as sent to the frontend. rlsClause is null for national users (they see everything);
// capabilities lists what they may do (DASHBOARDS, BUILD, MANAGE_KPIS, FILL_FORMS, REVIEW).
public record Viewer(UUID personId, String username, String fullName, List<String> roles,
                     List<Place> places, List<String> scopes, String rlsClause,
                     List<String> capabilities) {

    // The school ids where this user holds the given role, e.g. their schools as registrar.
    public List<String> schoolsFor(String role) {
        // Keep the places of that role that have a school, and take the school ids.
        return places.stream().filter(p -> p.role().equals(role) && p.schoolId() != null)
                .map(Place::schoolId).toList();
    }

    // The districts where this user is DEO.
    public List<String> deoDistricts() {
        // Keep the DEO places and take their district codes.
        return places.stream().filter(p -> p.role().equals("DEO")).map(Place::districtCode).toList();
    }
}
