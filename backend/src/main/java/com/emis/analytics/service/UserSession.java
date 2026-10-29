// Services: the rules of the app and the SQL that reads and writes the database.
package com.emis.analytics.service;

// Imports: the login session, time types, logging, settings and HTTP error helpers.
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

// Keeps track of who is logged in, using the browser's session cookie, and when they were last
// active. A session idle for longer than emis.session.idle-timeout is ended.
@Component
public class UserSession {

    // Name under which the session stores the username (the request log reads it too).
    public static final String USERNAME_ATTRIBUTE = "username";
    // Name under which the session stores the time of the user's last activity.
    private static final String LAST_ACTIVITY = "lastActivity";

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(UserSession.class);

    // How long a user may be idle before being logged out (emis.session.idle-timeout).
    @Value("${emis.session.idle-timeout}")
    private Duration idleTimeout;

    // Marks the session as logged in as this user, starting the idle clock now.
    public void start(HttpSession session, String username) {
        // Remember who is logged in.
        session.setAttribute(USERNAME_ATTRIBUTE, username);
        // Remember that they were active just now.
        session.setAttribute(LAST_ACTIVITY, Instant.now());
        // From now on, this request's log lines go to this user's log file.
        MDC.put("user", username);
        // Now we will log the new session.
        log.info("Session started for {} (idle timeout {})", username, idleTimeout);
    }

    // Records that the user did something, which restarts the idle clock.
    public void recordActivity(HttpSession session) {
        // First make sure the user is still logged in.
        requireUsername(session);
        // Remember that they were active just now.
        session.setAttribute(LAST_ACTIVITY, Instant.now());
        // Now we will log it (detail level).
        log.debug("Idle clock restarted");
    }

    // Returns the logged-in username, or stops the request with HTTP 401 if there is none.
    // It does NOT restart the idle clock, so background calls cannot keep a session alive.
    public String requireUsername(HttpSession session) {
        // Read who is logged in.
        String username = (String) session.getAttribute(USERNAME_ATTRIBUTE);
        // Nobody logged in on this browser.
        if (username == null) {
            // Now we will log why the request is refused.
            log.debug("No user in this session");
            // Send HTTP 401 Unauthorized with this message.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in.");
        }
        // Read when the user was last active.
        Instant lastActivity = (Instant) session.getAttribute(LAST_ACTIVITY);
        // How long ago that was.
        Duration idle = Duration.between(lastActivity, Instant.now());
        // If they have been idle for too long ...
        if (idle.compareTo(idleTimeout) > 0) {
            // ... log it ...
            log.info("Session of {} timed out after {} idle", username, idle.withNanos(0));
            // ... end the session ...
            session.invalidate();
            // ... and send HTTP 401 with this message.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Your session timed out. Please log in again.");
        }
        // Now we will log the check (detail level).
        log.debug("Logged in as {} (idle {} s of {})", username, idle.toSeconds(), idleTimeout);
        // Return the username.
        return username;
    }

    // Logs the user out by throwing the session away.
    public void end(HttpSession session) {
        // Now we will log who is leaving.
        log.info("Session ended for {}", session.getAttribute(USERNAME_ATTRIBUTE));
        // Delete the session and everything in it.
        session.invalidate();
    }
}
