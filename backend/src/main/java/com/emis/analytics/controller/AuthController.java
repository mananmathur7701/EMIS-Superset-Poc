// Controllers: they answer the browser's HTTP requests and send back JSON.
package com.emis.analytics.controller;

// Imports: the HTTP request and session, logging, password checking and Spring's web annotations.
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
// Our own classes used here.
import com.emis.analytics.dto.request.LoginRequest;
import com.emis.analytics.dto.request.SwitchRequest;
import com.emis.analytics.model.Viewer;
import com.emis.analytics.service.AccessService;
import com.emis.analytics.service.UserSession;

// Log in, log out, "who am I", activity pings and the demo account switcher.
@RestController
// Every URL in this class starts with /api/auth.
@RequestMapping("/api/auth")
public class AuthController {

    // Logger for writing messages to the console.
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // Checks typed passwords against the BCrypt hashes in the account table.
    private final BCryptPasswordEncoder passwordChecker = new BCryptPasswordEncoder();
    // Reads accounts and roles.
    private final AccessService accessService;
    // Keeps track of who is logged in.
    private final UserSession userSession;

    // Whether the demo "switch account" dropdown is allowed (emis.demo-switch-enabled).
    @Value("${emis.demo-switch-enabled}")
    private boolean demoSwitchEnabled;

    // Spring passes both helpers in when it creates the controller.
    public AuthController(AccessService accessService, UserSession userSession) {
        // Keep the access service.
        this.accessService = accessService;
        // Keep the session helper.
        this.userSession = userSession;
    }

    // POST /api/auth/login with {"username": "...", "password": "..."}: logs the user in.
    @PostMapping("/login")
    public Viewer login(@RequestBody LoginRequest login, HttpServletRequest request) {
        // Now we will log the attempt (never the password).
        log.info("Login attempt for {}", login.username());
        // Now we will look up the stored password hash for this username.
        String passwordHash = accessService.findPasswordHash(login.username());
        // Unknown username or wrong password: refuse, without saying which one was wrong.
        if (passwordHash == null || !passwordChecker.matches(login.password(), passwordHash)) {
            // Now we will log the failure, saying which check failed (only in the log, not to the browser).
            log.warn("Login failed for {}: {}", login.username(), passwordHash == null ? "no active account" : "wrong password");
            // Send HTTP 401 Unauthorized with this message.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong username or password.");
        }
        // The password is right; the user must also hold a current role (otherwise HTTP 403).
        Viewer viewer = accessService.requireViewer(login.username());
        // Make sure the browser has a session ...
        HttpSession session = request.getSession();
        // ... and give it a new id, so an id known before login cannot be reused (session fixation).
        request.changeSessionId();
        // Mark the session as logged in as this user.
        userSession.start(session, login.username());
        // Now we will write to the console who logged in.
        log.info("{} logged in", login.username());
        // Send the user's details to the frontend.
        return viewer;
    }

    // POST /api/auth/logout: logs the user out.
    @PostMapping("/logout")
    // Answer with HTTP 204 No Content (an empty reply).
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession session) {
        // Now we will write to the console who logged out.
        log.info("{} logged out", session.getAttribute("username"));
        // Throw the session away.
        userSession.end(session);
    }

    // GET /api/auth/me: who is logged in on this browser (HTTP 401 if nobody, or timed out).
    @GetMapping("/me")
    public Viewer me(HttpSession session) {
        // Opening or reloading the app counts as activity (this also checks the login).
        userSession.recordActivity(session);
        // Now we will return the logged-in user's details, freshly read from the database.
        return accessService.requireViewer(userSession.requireUsername(session));
    }

    // POST /api/auth/activity: the frontend calls this while the user is active on the page.
    @PostMapping("/activity")
    // Answer with HTTP 204 No Content (an empty reply).
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activity(HttpSession session) {
        // Restart the idle clock (HTTP 401 if the session already timed out).
        userSession.recordActivity(session);
    }

    // GET /api/auth/demo-users: the accounts the demo dropdown can switch to.
    @GetMapping("/demo-users")
    public List<Viewer> demoUsers(HttpSession session) {
        // Only logged-in users may see the list.
        userSession.requireUsername(session);
        // When the switcher is turned off, return an empty list (the frontend then hides it).
        if (!demoSwitchEnabled) {
            // Nothing to switch to.
            return List.of();
        }
        // Now we will return every active user who holds a current role.
        return accessService.allViewers();
    }

    // POST /api/auth/switch with {"username": "..."}: demo only, become another user without a password.
    @PostMapping("/switch")
    public Viewer switchUser(@RequestBody SwitchRequest switchTo, HttpSession session) {
        // Refuse when the switcher is turned off.
        if (!demoSwitchEnabled) {
            // Send HTTP 403 Forbidden with this message.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Switching accounts is turned off.");
        }
        // Only a logged-in user may switch.
        String previousUser = userSession.requireUsername(session);
        // The new user must hold a current role (otherwise HTTP 403).
        Viewer viewer = accessService.requireViewer(switchTo.username());
        // Mark the session as logged in as the new user.
        userSession.start(session, switchTo.username());
        // Now we will write the switch to the console.
        log.info("{} switched to {} (demo)", previousUser, switchTo.username());
        // Send the new user's details to the frontend.
        return viewer;
    }
}
