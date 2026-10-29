// Requests: the JSON bodies the frontend sends to the backend.
package com.emis.analytics.dto.request;

// JSON body of /login.
public record LoginRequest(String username, String password) {
}
