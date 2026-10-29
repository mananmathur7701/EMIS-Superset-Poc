// Model: data the backend reads from the database and sends to the frontend as JSON.
package com.emis.analytics.model;

// Imports: the types this record uses.
import java.util.List;
import java.util.UUID;

// One KPI in the registry screen, with its field links and scope.
public record KpiDetail(UUID id, String code, String name, String category, String description,
                        int multiplier, String status, String numeratorFieldId,
                        String denominatorFieldId, String numeratorField, String denominatorField,
                        List<String> scopeSchoolTypes, List<String> scopeDistrictCodes,
                        List<String> scopeSchoolIds, String metricName, String metricExpression) {
}
