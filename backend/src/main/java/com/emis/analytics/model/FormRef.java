// Model: data the backend reads from the database and sends to the frontend as JSON.
package com.emis.analytics.model;

// Imports: the types this record uses.
import java.util.UUID;

// A short reference to a form.
public record FormRef(UUID id, String code, String name, String description) {
}
