// Requests: the JSON bodies the frontend sends to the backend.
package com.emis.analytics.dto.request;

// Imports: the types this record uses.
import java.util.Map;

// JSON body of PUT /form/{formId}/school/{schoolId}.
public record SaveReturnRequest(Map<String, Object> data, boolean submit) {
}
