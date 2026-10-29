// Clients: code that calls other systems (here, Superset's REST API).
package com.emis.analytics.client;

// Imports: Java collections and Spring's HTTP client classes.
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
// Our own classes used here.
import com.emis.analytics.config.SupersetProperties;
import com.emis.analytics.model.Kpi;

// Calls Superset's REST API with the service account. Each method logs in afresh, so no Superset
// session is kept between requests.
@Component
public class SupersetClient {

    // Superset search that finds the census_frozen dataset (written in Superset's "rison" format).
    private static final String CENSUS_DATASET_QUERY =
            "(columns:!(id),filters:!((col:table_name,opr:eq,value:census_frozen)))";
    // Superset search that lists dashboards, newest change first.
    private static final String DASHBOARD_LIST_QUERY =
            "(columns:!(id,dashboard_title,slug,published,changed_on_delta_humanized),"
                    + "order_column:changed_on,order_direction:desc,page_size:100)";
    // Metric fields Superset accepts back when we update a dataset.
    private static final Set<String> METRIC_FIELDS = Set.of("id", "metric_name", "verbose_name",
            "metric_type", "expression", "description", "d3format", "currency", "extra", "warning_text");
    // Column fields Superset accepts back when we update a dataset.
    private static final Set<String> COLUMN_FIELDS = Set.of("id", "column_name", "type", "verbose_name",
            "description", "expression", "filterable", "groupby", "is_active", "is_dttm",
            "python_date_format", "extra", "advanced_data_type");

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(SupersetClient.class);

    // The HTTP client, pointed at Superset.
    private final RestClient http;
    // The superset.* settings from application.yml.
    private final SupersetProperties settings;

    // Spring passes in a RestClient builder and the settings when it creates this class.
    public SupersetClient(RestClient.Builder builder, SupersetProperties settings) {
        // Build an HTTP client whose requests all go to Superset's address.
        this.http = builder.baseUrl(settings.baseUrl()).build();
        // Keep the settings for later.
        this.settings = settings;
    }

    // True when Superset answers on its health page (used by the start-up report).
    public boolean healthy() {
        // A refused connection or an error answer both mean "not healthy".
        try {
            // Now we will ask Superset's health page, which answers "OK".
            String answer = http.get().uri("/health").retrieve().body(String.class);
            // Now we will log the answer (detail level).
            log.debug("GET /health answered {}", answer);
            // Healthy if it said OK.
            return "OK".equalsIgnoreCase(answer == null ? "" : answer.trim());
        // Not running, or broken.
        } catch (Exception e) {
            // Now we will log why.
            log.debug("GET /health failed: {}", e.getMessage());
            // Not healthy.
            return false;
        }
    }

    // Asks Superset for a guest token. The body holds the user, the dashboard and the RLS rules.
    public String createGuestToken(Map<String, Object> body) {
        // Now we will log what the pass is for (the token itself is never logged).
        log.debug("Asking Superset for a guest token: resources {}, row filter {}", body.get("resources"), body.get("rls"));
        // Now we will log in and send the request.
        Map<?, ?> reply = call(login(), HttpMethod.POST, "/api/v1/security/guest_token/", body);
        // The guest token is in the "token" field.
        return (String) reply.get("token");
    }

    // Returns the embed id (a UUID) of a dashboard, by its numeric id or its slug.
    public String embeddedId(String dashboardIdOrSlug) {
        // Now we will log the call to Superset (detail level).
        log.debug("Superset call: GET /api/v1/dashboard/{}/embedded", dashboardIdOrSlug);
        // Now we will ask Superset for the dashboard's embedding settings.
        Map<?, ?> reply = http.get()
                // Superset's "embedded dashboard" endpoint.
                .uri("/api/v1/dashboard/{id}/embedded", dashboardIdOrSlug)
                // Prove who we are.
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login().accessToken())
                // Send the request.
                .retrieve()
                // A 404 means the dashboard is not shared for embedding, so tell the frontend clearly.
                .onStatus(status -> status.value() == 404, (request, response) -> {
                    // Send HTTP 503 with a hint to the frontend.
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "This dashboard is not shared yet. Ask the CPU analyst to publish it again.");
                })
                // Read the JSON reply.
                .body(Map.class);
        // The embed id is in result.uuid.
        return (String) ((Map<?, ?>) reply.get("result")).get("uuid");
    }

    // Lets the EMIS frontend embed a dashboard (Superset keeps the same embed id if it already can).
    public void enableEmbedding(int dashboardId) {
        // Now we will log what is allowed.
        log.info("Allowing {} to embed dashboard {}", settings.embedAllowedDomains(), dashboardId);
        // The frontend addresses allowed to show the dashboard.
        Map<String, Object> body = Map.of("allowed_domains", settings.embedAllowedDomains());
        // Now we will log in and save the embedding settings.
        call(login(), HttpMethod.POST, "/api/v1/dashboard/" + dashboardId + "/embedded", body);
    }

    // Returns every Superset dashboard: id, title, slug, published flag and when it last changed.
    public List<Map<String, Object>> listDashboards() {
        // Now we will log in and run the search.
        Map<?, ?> reply = get(login(), "/api/v1/dashboard/?q={q}", DASHBOARD_LIST_QUERY);
        // The dashboards we will return.
        List<Map<String, Object>> dashboards = new ArrayList<>();
        // Go through each dashboard in the "result" list.
        for (Object item : (List<?>) reply.get("result")) {
            // Read it as a map.
            Map<?, ?> dashboard = (Map<?, ?>) item;
            // A slug may be missing, so use a mutable map (Map.of refuses nulls).
            Map<String, Object> row = new LinkedHashMap<>();
            // Superset's numeric id.
            row.put("id", ((Number) dashboard.get("id")).intValue());
            // The title.
            row.put("title", dashboard.get("dashboard_title"));
            // The short name, if any.
            row.put("slug", dashboard.get("slug"));
            // Whether it is published in Superset.
            row.put("published", dashboard.get("published"));
            // When it last changed, e.g. "2 hours ago".
            row.put("changed", dashboard.get("changed_on_delta_humanized"));
            // Add it to the list.
            dashboards.add(row);
        }
        // Return the list.
        return dashboards;
    }

    // Returns the numeric id of the dashboard with this slug, or null if there is none.
    public Integer dashboardIdBySlug(String slug) {
        // Now we will log the call to Superset (detail level).
        log.debug("Superset call: GET /api/v1/dashboard/{}", slug);
        // Now we will log in and ask for the dashboard.
        ResponseEntity<Map> reply = http.get()
                // Superset's dashboard endpoint accepts a slug in place of the id.
                .uri("/api/v1/dashboard/{slug}", slug)
                // Prove who we are.
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login().accessToken())
                // Send the request.
                .retrieve()
                // A 404 is not an error here: it just means "no such dashboard yet".
                .onStatus(status -> status.value() == 404, (request, response) -> { })
                // Read the reply with its status.
                .toEntity(Map.class);
        // Not found: no id.
        if (reply.getStatusCode().value() == 404) return null;
        // The id is in result.id.
        return ((Number) ((Map<?, ?>) reply.getBody().get("result")).get("id")).intValue();
    }

    // Returns Superset's numeric id for the census_frozen dataset, needed to open it in Explore.
    public long censusDatasetId() {
        // Now we will log in and search Superset's datasets for census_frozen.
        return censusDatasetId(login());
    }

    // Makes Superset re-read the census view's columns, then writes one metric per KPI and a
    // friendly label per column. columnLabels maps column names to labels, e.g. "total_pupils".
    // knownMetricNames lists every KPI that still exists: a KPI formula ("kpi_...") not in it
    // belongs to a deleted KPI and is removed.
    public void syncCensusDataset(List<Kpi> kpis, Map<String, String> columnLabels, List<String> knownMetricNames) {
        // Log in once for every request below.
        SupersetLogin login = login();
        // Find the dataset.
        long datasetId = censusDatasetId(login);
        // Re-read the view's columns, in case the view has changed.
        call(login, HttpMethod.PUT, "/api/v1/dataset/" + datasetId + "/refresh", null);
        // Now we will read the dataset's current metrics and columns.
        Map<?, ?> dataset = (Map<?, ?>) get(login, "/api/v1/dataset/" + datasetId).get("result");

        // Keep the existing metrics (such as COUNT(*)), looked up by name.
        Map<String, Map<String, Object>> metrics = new LinkedHashMap<>();
        // Go through each existing metric.
        for (Object item : (List<?>) dataset.get("metrics")) {
            // Copy the fields Superset accepts back.
            Map<String, Object> metric = keepFields((Map<?, ?>) item, METRIC_FIELDS);
            // Its name, e.g. "kpi_tch_01" or "count".
            String name = (String) metric.get("metric_name");
            // A KPI formula whose KPI no longer exists: leave it out, which removes it.
            if (name.startsWith("kpi_") && !knownMetricNames.contains(name)) {
                // Now we will log the removal.
                log.info("Removing Superset formula {}: its KPI no longer exists", name);
                // Skip it.
                continue;
            }
            // Remember it under its name.
            metrics.put(name, metric);
        }
        // Go through every active KPI.
        for (Kpi kpi : kpis) {
            // Start from the existing metric with this name, or a new empty one.
            Map<String, Object> metric = metrics.computeIfAbsent(kpi.metricName(), name -> new LinkedHashMap<>());
            // Metric name, e.g. "kpi_tch_01".
            metric.put("metric_name", kpi.metricName());
            // Display name, e.g. "Trained teacher %".
            metric.put("verbose_name", kpi.name());
            // The SQL formula.
            metric.put("expression", kpi.metricExpression());
            // A description that includes the KPI code.
            metric.put("description", kpi.code() + ": " + (kpi.description() == null ? "" : kpi.description()));
            // Show numbers with one decimal place.
            metric.put("d3format", ",.1f");
            // Now we will log the formula sent (detail level).
            log.debug("Formula {} \"{}\" = {}", kpi.metricName(), kpi.name(), kpi.metricExpression());
        }

        // The columns we will send back.
        List<Map<String, Object>> columns = new ArrayList<>();
        // Go through each column.
        for (Object item : (List<?>) dataset.get("columns")) {
            // Copy the fields Superset accepts back.
            Map<String, Object> column = keepFields((Map<?, ?>) item, COLUMN_FIELDS);
            // Look up a friendly label for it.
            String label = columnLabels.get((String) column.get("column_name"));
            // Use it if there is one.
            if (label != null) column.put("verbose_name", label);
            // Add the column.
            columns.add(column);
        }

        // What we send back (a HashMap, because Map.of refuses the empty value below).
        Map<String, Object> update = new HashMap<>();
        // The metrics.
        update.put("metrics", new ArrayList<>(metrics.values()));
        // The columns.
        update.put("columns", columns);
        // The view has no date column, so Explore must not add a time filter on one by default.
        update.put("main_dttm_col", null);
        // Now we will save everything back to Superset.
        call(login, HttpMethod.PUT, "/api/v1/dataset/" + datasetId, update);
        // Now we will log what was saved.
        log.info("Dataset {} updated: {} formulas ({}), {} columns", datasetId, metrics.size(), metrics.keySet(), columns.size());
    }

    // Finds the census_frozen dataset's id with an existing login.
    private long censusDatasetId(SupersetLogin login) {
        // Now we will search Superset's datasets for census_frozen.
        List<?> datasets = (List<?>) get(login, "/api/v1/dataset/?q={q}", CENSUS_DATASET_QUERY).get("result");
        // An empty list means the dataset has not been set up yet.
        if (datasets.isEmpty()) {
            // Send HTTP 503 with a hint to the frontend.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Dataset census_frozen is not in Superset yet. Has superset-provision run?");
        }
        // Return the first match's id.
        return ((Number) ((Map<?, ?>) datasets.get(0)).get("id")).longValue();
    }

    // Sends a GET request and returns the JSON reply. Extra values fill {placeholders} in the path.
    private Map<?, ?> get(SupersetLogin login, String path, Object... values) {
        // Now we will log the call to Superset (detail level).
        log.debug("Superset call: GET {}", path);
        // Now we will send the request.
        return http.get()
                // The address, with any placeholders filled in (and safely encoded).
                .uri(path, values)
                // Prove who we are.
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                // Send it.
                .retrieve()
                // Read the JSON reply.
                .body(Map.class);
    }

    // Sends a request that changes something (POST or PUT) and returns the JSON reply.
    private Map<?, ?> call(SupersetLogin login, HttpMethod method, String path, Object body) {
        // Now we will log the call to Superset (detail level).
        log.debug("Superset call: {} {}", method, path);
        // Start the request.
        RestClient.RequestBodySpec request = http.method(method)
                // The address.
                .uri(path)
                // Prove who we are with the access token ...
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                // ... send the CSRF token ...
                .header("X-CSRFToken", login.csrfToken())
                // ... with its session cookie ...
                .header(HttpHeaders.COOKIE, login.sessionCookie())
                // ... and say where the request comes from, which Superset also checks.
                .header(HttpHeaders.REFERER, settings.baseUrl());
        // Attach the body as JSON, if there is one.
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body);
        // Now we will send it and read the JSON reply.
        Map<?, ?> reply = request.retrieve().body(Map.class);
        // Some replies have no body; return an empty map then.
        return reply == null ? Map.of() : reply;
    }

    // Logs in to Superset with the service account. Returns the access token, plus the CSRF token
    // and session cookie that requests which change something also need.
    private SupersetLogin login() {
        // Now we will log the login (detail level; the password is never logged).
        log.debug("Superset call: POST /api/v1/security/login as {}, then GET /api/v1/security/csrf_token/", settings.username());
        // The login details from application.yml.
        Map<String, Object> credentials = Map.of(
                // Superset username.
                "username", settings.username(),
                // Superset password.
                "password", settings.password(),
                // Login type ("db").
                "provider", settings.provider(),
                // We do not need a refresh token.
                "refresh", false);
        // Now we will send the login request.
        Map<?, ?> reply = http.post()
                // Superset's login endpoint.
                .uri("/api/v1/security/login")
                // The body is JSON.
                .contentType(MediaType.APPLICATION_JSON)
                // Attach the login details.
                .body(credentials)
                // Send the request.
                .retrieve()
                // Read the JSON reply.
                .body(Map.class);
        // The token is in the "access_token" field.
        String accessToken = (String) reply.get("access_token");

        // Now we will fetch a CSRF token.
        ResponseEntity<Map> csrfReply = http.get()
                // Superset's CSRF endpoint.
                .uri("/api/v1/security/csrf_token/")
                // Prove who we are with the access token.
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                // Send the request.
                .retrieve()
                // Read the JSON body and keep the response headers too.
                .toEntity(Map.class);
        // The CSRF token is in the "result" field.
        String csrfToken = (String) csrfReply.getBody().get("result");
        // It only works together with the session cookie Superset just set, so keep that too.
        String sessionCookie = csrfReply.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";")[0];
        // Return all three.
        return new SupersetLogin(accessToken, csrfToken, sessionCookie);
    }

    // Returns a copy of a Superset item with only the allowed fields that have a value.
    private static Map<String, Object> keepFields(Map<?, ?> item, Set<String> allowed) {
        // The copy.
        Map<String, Object> copy = new LinkedHashMap<>();
        // Go through each field of the item.
        for (Map.Entry<?, ?> entry : item.entrySet()) {
            // Keep it only if it is allowed and not empty.
            if (allowed.contains(entry.getKey()) && entry.getValue() != null) {
                // Copy the field.
                copy.put((String) entry.getKey(), entry.getValue());
            }
        }
        // Return the copy.
        return copy;
    }
}
