// Model: data the backend reads from the database and sends to the frontend as JSON.
package com.emis.analytics.model;

// Imports: the types this record uses.
import java.util.List;
import java.util.UUID;

// One form in the list.
public record FormSummary(UUID id, String code, String name, String description, String status,
                          List<String> kpiIds, List<String> kpiCodes, int questionCount,
                          String updatedAt) {
}
