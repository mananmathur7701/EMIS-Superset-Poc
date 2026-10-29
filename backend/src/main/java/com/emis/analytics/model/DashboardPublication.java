// Model: data the backend reads from the database and sends to the frontend as JSON.
package com.emis.analytics.model;

// Imports: the types this record uses.
import java.util.List;

// A dashboard and the roles it is published to.
public record DashboardPublication(int dashboardId, String title, List<String> roles) {
}
