// Requests: the JSON bodies the frontend sends to the backend.
package com.emis.analytics.dto.request;

// Imports: the types this record uses.
import java.util.List;

// JSON body of PUT /dashboards/{id}.
public record PublishRequest(String title, List<String> roles) {
}
