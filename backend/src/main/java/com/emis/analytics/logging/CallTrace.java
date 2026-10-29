// Logging: the request log, the call trace and the startup report.
package com.emis.analytics.logging;

// Imports: Java lists and the logging context (MDC) that every log line can print from.
import java.util.ArrayList;
import java.util.List;
import org.slf4j.MDC;

// Remembers, for the request being handled on this thread, which of our methods were called and how
// deep each call is. The log pattern prints the depth as indentation (the MDC value "indent"), so
// the log reads like a tree: endpoint, then controller, then service, then client.
public final class CallTrace {

    // How deep the current call is: 0 means "not inside any traced method".
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    // Every traced call so far in this request, already indented, e.g. "  KpiService.save".
    private static final ThreadLocal<List<String>> STEPS = ThreadLocal.withInitial(ArrayList::new);
    // The last error already written to the log, so outer methods don't write it again.
    private static final ThreadLocal<Throwable> REPORTED = new ThreadLocal<>();

    // Nobody creates a CallTrace; it only has static methods.
    private CallTrace() {
    }

    // A traced method starts: remember it and go one level deeper. Returns the depth it started at.
    public static int enter(String method) {
        // How deep we are now.
        int depth = DEPTH.get();
        // Remember the call, indented by its depth.
        STEPS.get().add("  ".repeat(depth) + method);
        // Everything logged inside this method is one level deeper.
        DEPTH.set(depth + 1);
        // Tell the log pattern to indent by the new depth.
        MDC.put("indent", "  ".repeat(depth + 1));
        // Hand back the starting depth, for exit().
        return depth;
    }

    // A traced method ends: go back to the depth it started at.
    public static void exit(int depth) {
        // Back to the starting depth.
        DEPTH.set(depth);
        // Indent the next lines by that depth again.
        MDC.put("indent", "  ".repeat(depth));
    }

    // Returns the calls made so far in this request, one per line, indented as a tree.
    public static List<String> steps() {
        // A copy, so the caller cannot change ours.
        return List.copyOf(STEPS.get());
    }

    // True the first time an error is seen (so it is logged once, where it happened).
    public static boolean firstReport(Throwable error) {
        // Already logged by a deeper method.
        if (REPORTED.get() == error) return false;
        // Remember it.
        REPORTED.set(error);
        // First time.
        return true;
    }

    // Forgets everything at the end of a request (threads are reused for the next request).
    public static void clear() {
        // Forget the depth.
        DEPTH.remove();
        // Forget the calls.
        STEPS.remove();
        // Forget the logged error.
        REPORTED.remove();
        // No indentation.
        MDC.remove("indent");
    }
}
