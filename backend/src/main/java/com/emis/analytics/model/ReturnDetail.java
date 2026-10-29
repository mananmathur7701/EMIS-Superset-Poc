// Model: data the backend reads from the database and sends to the frontend as JSON.
package com.emis.analytics.model;

// Imports: the types this record uses.
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

// A return with the school's form, the answers and the history.
public record ReturnDetail(ReturnSummary summary, JsonNode schema, Map<String, Object> data,
                           List<ReturnEvent> history, boolean canEdit) {
}
