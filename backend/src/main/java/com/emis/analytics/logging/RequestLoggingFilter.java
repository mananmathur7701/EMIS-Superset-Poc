// Logging: the request log, the call trace and the startup report.
package com.emis.analytics.logging;

// Imports: servlet types, Java helpers, logging and Spring's filter base class.
import com.emis.analytics.service.UserSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// The first thing every HTTP request passes through. It gives the request an id and notes who is
// asking (both are printed on every log line of the request, and the user decides which per-user
// log file the lines go to). It logs the endpoint when the request starts, and the status, time
// and the full call trace (controller → services → client) when it ends.
@Component
// Run before every other filter, so even errors in them are inside the request's log.
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    // Header the frontend sends (and we send back) to connect browser console lines to backend lines.
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    // Handles one request: sets up the log context, runs the request, logs the outcome.
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Use the frontend's request id if it sent a sensible one, otherwise make a short one.
        String requestId = pickRequestId(request.getHeader(REQUEST_ID_HEADER));
        // The endpoint, e.g. "POST /api/admin/kpis" (with the query string, masked).
        String endpoint = request.getMethod() + " " + request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + LogSafety.mask(request.getQueryString()));
        // Every log line of this request shows the request id ...
        MDC.put("requestId", requestId);
        // ... and the user (login and switch update it once they know who it is).
        MDC.put("user", currentUser(request));
        // Send the id back, so the browser console can show it too.
        response.setHeader(REQUEST_ID_HEADER, requestId);
        // Note the start time.
        long start = System.nanoTime();
        // Now we will log the start of the request.
        log.info("→ {} (from {})", endpoint, request.getRemoteAddr());
        // Run the request, making sure the end is always logged.
        try {
            // Now we will pass the request on (to Spring, then a controller).
            chain.doFilter(request, response);
        // An error nobody handled: log it with its stack trace, then let the server answer 500.
        } catch (IOException | ServletException | RuntimeException error) {
            // Now we will log it (unless the call trace already did).
            if (CallTrace.firstReport(error)) log.error("✖ {} failed with an unexpected error", endpoint, error);
            // Pass it on.
            throw error;
        } finally {
            // How long it took.
            long millis = (System.nanoTime() - start) / 1_000_000;
            // Now we will log the end with the HTTP status.
            log.info("← {} answered {} in {} ms", endpoint, response.getStatus(), millis);
            // The methods this request went through, as an indented tree.
            var steps = CallTrace.steps();
            // If our code ran, log the whole call trace in one block.
            if (!steps.isEmpty()) {
                // One block, one call per line.
                log.info("Call trace of {}:\n    {}", endpoint, String.join("\n    ", steps));
            }
            // Forget this request's trace (the thread will serve other requests).
            CallTrace.clear();
            // Forget this request's log context.
            MDC.clear();
        }
    }

    // Returns the username logged in on this browser, or "anonymous".
    private static String currentUser(HttpServletRequest request) {
        // The session, without creating one.
        HttpSession session = request.getSession(false);
        // No session: nobody logged in.
        if (session == null) return "anonymous";
        // Read who is logged in.
        Object username = session.getAttribute(UserSession.USERNAME_ATTRIBUTE);
        // Nobody, or the name.
        return username == null ? "anonymous" : username.toString();
    }

    // Accepts the frontend's id if it is short and plain, otherwise makes a new 8-character one.
    private static String pickRequestId(String fromHeader) {
        // Letters, digits and dashes only, at most 36 characters.
        if (fromHeader != null && fromHeader.matches("[A-Za-z0-9-]{1,36}")) return fromHeader;
        // A new random id.
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
