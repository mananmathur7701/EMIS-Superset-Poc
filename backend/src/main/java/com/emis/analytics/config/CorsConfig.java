// Settings classes: values from application.yml and web settings.
package com.emis.analytics.config;

// Imports: Spring's settings annotations and the CORS registry.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Tell Spring this class holds web settings.
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // Frontend addresses allowed to call this API directly (emis.cors-allowed-origins). The bundled
    // frontend does not need this, because it calls /api on its own address.
    @Value("${emis.cors-allowed-origins}")
    private String[] allowedOrigins;

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    // Spring calls this method once at startup to collect the CORS rules.
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Now we will log the rule being set up.
        log.info("Start-up: allowing browser calls (CORS, with cookies) from {}", String.join(", ", allowedOrigins));
        // Now we will allow the browser to call every endpoint of this API ...
        registry.addMapping("/**")
                // ... but only from the frontend's address ...
                .allowedOrigins(allowedOrigins)
                // ... only with these HTTP methods ...
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // ... and with the login cookie, so a frontend on another port can stay logged in.
                .allowCredentials(true);
    }
}
