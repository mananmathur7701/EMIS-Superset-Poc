// Settings classes: values from application.yml and web settings.
package com.emis.analytics.config;

// Imports: a Java list and the annotation that reads settings from application.yml.
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Read every "superset.*" setting from application.yml into this record.
@ConfigurationProperties(prefix = "superset")
public record SupersetProperties(
        // Address the backend uses to call Superset (superset.base-url).
        String baseUrl,
        // Address the browser uses for the builder iframe (superset.public-url).
        String publicUrl,
        // Address the browser uses for the embedded dashboard (superset.embed-url).
        String embedUrl,
        // Superset username the backend logs in with (superset.username).
        String username,
        // Password for that Superset user (superset.password).
        String password,
        // Superset login type, "db" for a normal account (superset.provider).
        String provider,
        // Short name of the dashboard every role gets until the CPU analyst publishes others
        // (superset.dashboard-slug).
        String dashboardSlug,
        // Frontend addresses allowed to embed a published dashboard (superset.embed-allowed-domains).
        List<String> embedAllowedDomains,
        // Secret shared with Superset for the CPU analyst's automatic sign-in (superset.sso-secret).
        String ssoSecret) {
}
