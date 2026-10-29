// Model: data the backend reads from the database and sends to the frontend as JSON.
package com.emis.analytics.model;

// Imports: the types this record uses.
import java.util.List;
import java.util.UUID;

// One field in the library, with the KPIs built from it.
public record Field(UUID id, String code, String label, String description, String fieldType,
                    String options, boolean active, List<String> usedByKpis) {
}
