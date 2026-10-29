// The root package: Spring Boot starts here and finds every class in the folders below.
package com.emis.analytics;

// Imports: logging, and Spring Boot's starter class and annotations.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Mark this as the Spring Boot application; Spring finds every class in this package and the
// folders below it (config, controller, service, client, model, dto).
@SpringBootApplication
// Also load settings records such as SupersetProperties from application.yml.
@ConfigurationPropertiesScan
public class EmisAnalyticsApplication {

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(EmisAnalyticsApplication.class);

    // Java starts the program here.
    public static void main(String[] args) {
        // Now we will log what is starting, on which Java, from which folder.
        log.info("Start-up: EMIS backend starting on Java {} in {}", System.getProperty("java.version"),
                System.getProperty("user.dir"));
        // Now we will start Spring Boot: it reads application.yml, runs schema.sql and data.sql,
        // creates every class, and starts the web server. Its own steps are logged as it goes.
        SpringApplication.run(EmisAnalyticsApplication.class, args);
        // Now we will log that start-up finished (the start-up report above lists what ran).
        log.info("Start-up: finished; waiting for requests");
    }
}
