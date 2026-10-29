// Logging: the request log, the call trace and the startup report.
package com.emis.analytics.logging;

// Imports: Spring's method-interception API, logging, and the HTTP error type.
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;
import org.springframework.web.server.ResponseStatusException;

// Wraps every public method of our controllers, services and Superset client (see CallTraceConfig).
// For each call it writes "▶ Class.method(arguments)" before and "◀ Class.method → result (ms)"
// after, indented by depth, so the log shows the whole chain: endpoint → controller → service →
// other service or client. Errors are written once, where they happen.
public class CallTraceInterceptor implements MethodInterceptor {

    // Runs instead of the real method, calls it, and logs around it.
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // The real class (not Spring's proxy), e.g. KpiService.
        Class<?> type = ClassUtils.getUserClass(invocation.getThis());
        // The name we log, e.g. "KpiService.save".
        String name = type.getSimpleName() + "." + invocation.getMethod().getName();
        // Log under the real class's name, so the log shows who wrote the line.
        Logger log = LoggerFactory.getLogger(type);
        // Now we will log the call with its (masked) arguments.
        log.info("▶ {}({})", name, LogSafety.describeAll(invocation.getArguments()));
        // Note the start time.
        long start = System.nanoTime();
        // Go one level deeper for everything this method calls or logs.
        int depth = CallTrace.enter(name);
        // Call the real method, catching errors so we can log them.
        try {
            // Now we will run the real method.
            Object result = invocation.proceed();
            // Back to this method's level.
            CallTrace.exit(depth);
            // Now we will log the result and how long it took.
            log.info("◀ {} → {} ({} ms)", name, LogSafety.describe(result), millis(start));
            // Hand the result back.
            return result;
        // An expected refusal, e.g. 403 "Your role does not allow this." or 400 "Give the KPI a name."
        } catch (ResponseStatusException refusal) {
            // Back to this method's level.
            CallTrace.exit(depth);
            // Where it happened: log the status and reason as a warning.
            if (CallTrace.firstReport(refusal)) {
                // Now we will log the refusal.
                log.warn("✖ {} refused: {} {} ({} ms)", name, refusal.getStatusCode().value(), refusal.getReason(), millis(start));
            // Outer methods only note that they passed it on.
            } else {
                // Now we will note it.
                log.info("✖ {} passed on the refusal ({} ms)", name, millis(start));
            }
            // Let Spring turn it into the HTTP answer.
            throw refusal;
        // An unexpected error (a bug, the database or Superset down…).
        } catch (Throwable error) {
            // Back to this method's level.
            CallTrace.exit(depth);
            // Where it happened: log the full Java stack trace once.
            if (CallTrace.firstReport(error)) {
                // Now we will log the error with its stack trace.
                log.error("✖ {} failed after {} ms: {}", name, millis(start), error.toString(), error);
            // Outer methods only note that they passed it on.
            } else {
                // Now we will note it.
                log.warn("✖ {} passed on the error ({} ms)", name, millis(start));
            }
            // Pass it on.
            throw error;
        }
    }

    // Milliseconds since a start time from System.nanoTime().
    private static long millis(long start) {
        // Nanoseconds to milliseconds.
        return (System.nanoTime() - start) / 1_000_000;
    }
}
