// Model: data the backend reads from the database and sends to the frontend as JSON.
package com.emis.analytics.model;

// Imports: the types this record uses.
import java.util.List;
import java.util.UUID;

// One KPI in the public catalogue, as sent to superset-provision.
public record Kpi(UUID id, String code, String name, String description, String numeratorField,
                  String denominatorField, int multiplier, List<String> sliceColumns,
                  String metricName, String metricExpression) {
}
