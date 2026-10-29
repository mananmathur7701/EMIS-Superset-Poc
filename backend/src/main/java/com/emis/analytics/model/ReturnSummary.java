// Model: data the backend reads from the database and sends to the frontend as JSON.
package com.emis.analytics.model;

// Imports: the types this record uses.
import java.util.UUID;

// One return in a list. id is null and status "NOT_STARTED" when nothing is saved yet.
public record ReturnSummary(UUID id, UUID formId, String formName, UUID schoolId, String schoolName,
                            String districtCode, String academicYear, String status, String updatedAt,
                            String lastReturnComment, boolean actionable) {
}
