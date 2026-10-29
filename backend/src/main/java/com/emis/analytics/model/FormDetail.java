// Model: data the backend reads from the database and sends to the frontend as JSON.
package com.emis.analytics.model;

// Imports: the types this record uses.
import tools.jackson.databind.JsonNode;

// One form with its Form.io definition.
public record FormDetail(FormSummary form, JsonNode schema) {
}
