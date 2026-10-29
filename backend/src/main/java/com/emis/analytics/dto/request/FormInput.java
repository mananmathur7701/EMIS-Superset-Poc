// Requests: the JSON bodies the frontend sends to the backend.
package com.emis.analytics.dto.request;

// Imports: the types this record uses.
import java.util.List;
import tools.jackson.databind.JsonNode;

// A form as the builder sends it.
public record FormInput(String code, String name, String description, String status,
                        List<String> kpiIds, JsonNode schema) {
}
