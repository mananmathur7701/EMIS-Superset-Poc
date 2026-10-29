// Services: the rules of the app and the SQL that reads and writes the database.
package com.emis.analytics.service;

// Imports: text encoding, the HMAC signer, Base64, time, Java collections and Jackson's JSON writer.
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
// Our own classes used here.
import com.emis.analytics.config.SupersetProperties;
import com.emis.analytics.model.Viewer;

// Makes the short-lived signed token (a JWT) that signs the CPU analyst in to Superset's builder.
// Superset's /emis/sso route (superset/superset_config.py) checks it with the same secret.
@Service
public class SsoTokenService {

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(SsoTokenService.class);

    // Who the token is for; Superset refuses tokens meant for anything else.
    private static final String AUDIENCE = "superset-sso";
    // How long the token works, in seconds: just long enough for the browser to open the link.
    private static final long LIFETIME_SECONDS = 60;

    // The superset.* settings (for the shared secret).
    private final SupersetProperties settings;
    // Writes JSON.
    private final JsonMapper json;

    // Spring passes the helpers in when it creates this service.
    public SsoTokenService(SupersetProperties settings, JsonMapper json) {
        // Keep the settings.
        this.settings = settings;
        // Keep the JSON helper.
        this.json = json;
    }

    // Makes a token for this user.
    public String createToken(Viewer viewer) {
        // Now we will log who gets a ticket (the ticket itself is never logged).
        log.info("Making a {}-second Superset sign-in ticket for {}", LIFETIME_SECONDS, viewer.username());
        // Split the full name into first name and the rest, e.g. "Kendell" and "St. Rose".
        String[] nameParts = viewer.fullName().split(" ", 2);
        // The token's contents ("claims").
        Map<String, Object> claims = new LinkedHashMap<>();
        // The username Superset signs in (it creates the Superset user the first time).
        claims.put("sub", viewer.username());
        // First name.
        claims.put("first_name", nameParts[0]);
        // Last name, or empty if there is none.
        claims.put("last_name", nameParts.length > 1 ? nameParts[1] : "");
        // Superset needs an email; this one is made up from the username.
        claims.put("email", viewer.username() + "@emis.local");
        // Who the token is for.
        claims.put("aud", AUDIENCE);
        // When it stops working (seconds since 1970).
        claims.put("exp", Instant.now().getEpochSecond() + LIFETIME_SECONDS);

        // The header says the token is signed with HMAC-SHA256.
        String header = encode(json.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
        // The claims, encoded the same way.
        String payload = encode(json.writeValueAsBytes(claims));
        // Now we will sign "header.payload" with the shared secret.
        String signature = encode(sign(header + "." + payload));
        // A JWT is the three parts joined with dots.
        return header + "." + payload + "." + signature;
    }

    // Signs text with HMAC-SHA256 and the shared secret.
    private byte[] sign(String text) {
        // Java's crypto API throws checked exceptions, which cannot happen here.
        try {
            // Get an HMAC-SHA256 signer.
            Mac mac = Mac.getInstance("HmacSHA256");
            // Give it the secret.
            mac.init(new SecretKeySpec(settings.ssoSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            // Now we will sign the text and return the signature.
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        // Turn the impossible checked exception into an unchecked one.
        } catch (Exception e) {
            // Stop with an error.
            throw new IllegalStateException("Could not sign the Superset sign-in token", e);
        }
    }

    // Encodes bytes the way JWTs need: URL-safe Base64 without "=" padding.
    private static String encode(byte[] bytes) {
        // Encode and return.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
