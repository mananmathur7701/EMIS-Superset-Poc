// Responses: JSON replies to the frontend that are not database data.
package com.emis.analytics.dto.response;

// JSON sent back by /builder.
public record BuilderResponse(String url, String supersetDomain) {
}
