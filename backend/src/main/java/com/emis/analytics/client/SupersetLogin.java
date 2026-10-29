// Clients: code that calls other systems (here, Superset's REST API).
package com.emis.analytics.client;

// What one Superset login gives us.
record SupersetLogin(String accessToken, String csrfToken, String sessionCookie) {
}
