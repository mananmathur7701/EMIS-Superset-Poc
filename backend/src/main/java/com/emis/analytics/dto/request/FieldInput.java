// Requests: the JSON bodies the frontend sends to the backend.
package com.emis.analytics.dto.request;

// A field as the editor sends it.
public record FieldInput(String code, String label, String description, String fieldType,
                         String options, boolean active) {
}
