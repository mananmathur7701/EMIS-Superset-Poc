// Logging: the request log, the call trace and the startup report.
package com.emis.analytics.logging;

// Imports: Java collections and regular expressions, servlet types, JSON nodes and our Viewer.
import com.emis.analytics.model.Viewer;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

// Turns method arguments and results into short, safe text for the log: passwords, tokens and
// password hashes are hidden, and long values are cut.
public final class LogSafety {

    // A JWT (guest token, sign-in ticket): three Base64 parts starting with "eyJ".
    private static final Pattern JWT = Pattern.compile("eyJ[\\w-]*\\.[\\w-]+\\.[\\w-]+");
    // A BCrypt password hash, e.g. $2a$10$...
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
    // "password=..." or "secret=..." inside a record's text.
    private static final Pattern SECRET_FIELD = Pattern.compile("(?i)((?:password|secret)\\w*=)[^,)\\]]*");

    // Nobody creates a LogSafety; it only has static methods.
    private LogSafety() {
    }

    // Hides passwords, tokens and hashes in a piece of text.
    public static String mask(String text) {
        // Nothing to hide in nothing.
        if (text == null) return null;
        // Hide tokens.
        String masked = JWT.matcher(text).replaceAll("eyJ…(token hidden)");
        // Hide password hashes.
        masked = BCRYPT.matcher(masked).replaceAll("(hash hidden)");
        // Hide password and secret fields, keeping the field name.
        return SECRET_FIELD.matcher(masked).replaceAll("$1****");
    }

    // Describes a method's arguments, e.g. "\"kstrose\", session".
    public static String describeAll(Object[] values) {
        // Describe each one and join them with commas.
        return Arrays.stream(values).map(LogSafety::describe).collect(Collectors.joining(", "));
    }

    // Describes one value briefly and safely.
    public static String describe(Object value) {
        // Nothing.
        if (value == null) return "null";
        // The login session: never print its contents.
        if (value instanceof HttpSession) return "session";
        // The HTTP request or response objects.
        if (value instanceof ServletRequest) return "request";
        // The HTTP response object.
        if (value instanceof ServletResponse) return "response";
        // Text: quoted, masked and cut to 80 characters.
        if (value instanceof CharSequence text) return "\"" + shorten(mask(text.toString()), 80) + "\"";
        // Numbers, yes/no values and ids are printed as they are.
        if (value instanceof Number || value instanceof Boolean || value instanceof UUID) return value.toString();
        // A user: name and roles only.
        if (value instanceof Viewer viewer) return "Viewer(" + viewer.username() + ", " + viewer.roles() + ")";
        // A list: just how many items.
        if (value instanceof Collection<?> list) return "list of " + list.size();
        // A map: just how many keys.
        if (value instanceof Map<?, ?> map) return "map of " + map.size() + " keys";
        // A JSON document (e.g. a Form.io design): just its size.
        if (value instanceof JsonNode node) return "JSON with " + node.size() + " entries";
        // A byte array: just its length.
        if (value instanceof byte[] bytes) return bytes.length + " bytes";
        // A record (our data classes): its fields, masked and cut to 200 characters.
        if (value.getClass().isRecord()) return shorten(mask(value.toString()), 200);
        // Anything else: its type only.
        return value.getClass().getSimpleName();
    }

    // Cuts text to a maximum length, adding "…" when it was longer.
    public static String shorten(String text, int max) {
        // Short enough already.
        if (text.length() <= max) return text;
        // Cut and mark it.
        return text.substring(0, max) + "…";
    }
}
