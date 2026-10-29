// Model: data the backend reads from the database and sends to the frontend as JSON.
package com.emis.analytics.model;

// One line of a return's history.
public record ReturnEvent(String action, String fromStatus, String toStatus, String comment,
                          String personName, String createdAt) {
}
