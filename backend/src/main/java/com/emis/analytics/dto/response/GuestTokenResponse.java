// Responses: JSON replies to the frontend that are not database data.
package com.emis.analytics.dto.response;

// JSON sent back by /auth/guest-token.
public record GuestTokenResponse(String token, String dashboardId, String supersetDomain) {
}
