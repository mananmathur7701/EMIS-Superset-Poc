// Requests: the JSON bodies the frontend sends to the backend.
package com.emis.analytics.dto.request;

// JSON body of POST /{id}/review.
public record ReviewRequest(String action, String comment) {
}
