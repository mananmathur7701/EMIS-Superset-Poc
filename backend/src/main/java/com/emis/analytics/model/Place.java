// Model: data the backend reads from the database and sends to the frontend as JSON.
package com.emis.analytics.model;

// One role held at one place: a school (schoolId), a district (districtCode), or neither.
public record Place(String role, String schoolId, String schoolName, String districtCode) {
}
