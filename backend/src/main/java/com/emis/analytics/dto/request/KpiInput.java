// Requests: the JSON bodies the frontend sends to the backend.
package com.emis.analytics.dto.request;

// Imports: the types this record uses.
import java.util.List;

// A KPI as the editor sends it. Empty scope lists mean "all".
public record KpiInput(String code, String name, String category, String description, int multiplier,
                       String status, String numeratorFieldId, String denominatorFieldId,
                       List<String> scopeSchoolTypes, List<String> scopeDistrictCodes,
                       List<String> scopeSchoolIds) {
}
