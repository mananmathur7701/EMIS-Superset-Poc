// Controllers: they answer the browser's HTTP requests and send back JSON.
package com.emis.analytics.controller;

// Imports: the login session, URL encoding, Java collections, logging and Spring's web annotations.
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
// Our own classes used here.
import com.emis.analytics.client.SupersetClient;
import com.emis.analytics.config.SupersetProperties;
import com.emis.analytics.dto.response.BuilderResponse;
import com.emis.analytics.dto.response.GuestTokenResponse;
import com.emis.analytics.model.DashboardPublication;
import com.emis.analytics.model.Kpi;
import com.emis.analytics.model.Viewer;
import com.emis.analytics.service.AccessService;
import com.emis.analytics.service.DashboardService;
import com.emis.analytics.service.KpiService;
import com.emis.analytics.service.SsoTokenService;
import com.emis.analytics.service.UserSession;

// Dashboards and the Superset builder.
@RestController
// Every URL in this class starts with /api/analytics.
@RequestMapping("/api/analytics")
public class AnalyticsController {

    // Logger for writing messages to the console.
    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    // Reads the KPI catalogue.
    private final KpiService kpiService;
    // Works out who the logged-in user is and what they may do.
    private final AccessService accessService;
    // Knows who is logged in.
    private final UserSession userSession;
    // Knows which dashboards each role may open.
    private final DashboardService dashboardService;
    // Makes the builder's sign-in token.
    private final SsoTokenService ssoTokens;
    // Talks to Superset.
    private final SupersetClient superset;
    // The superset.* settings from application.yml.
    private final SupersetProperties settings;

    // Spring passes all the helpers in when it creates the controller.
    public AnalyticsController(KpiService kpiService, AccessService accessService, UserSession userSession,
                               DashboardService dashboardService, SsoTokenService ssoTokens,
                               SupersetClient superset, SupersetProperties settings) {
        // Keep the KPI service.
        this.kpiService = kpiService;
        // Keep the access service.
        this.accessService = accessService;
        // Keep the session helper.
        this.userSession = userSession;
        // Keep the dashboard service.
        this.dashboardService = dashboardService;
        // Keep the token maker.
        this.ssoTokens = ssoTokens;
        // Keep the Superset client.
        this.superset = superset;
        // Keep the settings.
        this.settings = settings;
    }

    // GET /api/analytics/kpis: the active KPIs with their Superset formulas. No login needed:
    // it holds formulas, not census data, and superset-provision reads it at startup.
    @GetMapping("/kpis")
    public List<Kpi> kpis() {
        // Now we will read the active KPIs from the database and return them.
        return kpiService.findActiveKpis();
    }

    // GET /api/analytics/dashboards: the dashboards published to the user's roles.
    @GetMapping("/dashboards")
    public List<DashboardPublication> dashboards(HttpSession session) {
        // Find out who is logged in (HTTP 401 if nobody).
        Viewer viewer = accessService.requireViewer(userSession.requireUsername(session));
        // Only roles that open dashboards (HTTP 403 otherwise).
        AccessService.requireCapability(viewer, AccessService.DASHBOARDS);
        // Now we will return their dashboards.
        return dashboardService.forViewer(viewer);
    }

    // GET /api/analytics/auth/guest-token?dashboardId=1: a Superset guest token for one published
    // dashboard, limited to the user's rows. The embedded dashboard calls this again every few
    // minutes; once the user logs out or times out, it answers HTTP 401 and the dashboard stops.
    @GetMapping("/auth/guest-token")
    public GuestTokenResponse guestToken(@RequestParam int dashboardId, HttpSession session) {
        // Find out who is logged in (HTTP 401 if nobody). This does not count as activity.
        String username = userSession.requireUsername(session);
        // Build their details and row-level security (RLS) clause.
        Viewer viewer = accessService.requireViewer(username);
        // Only roles that open dashboards (HTTP 403 otherwise).
        AccessService.requireCapability(viewer, AccessService.DASHBOARDS);
        // The dashboard must be published to one of their roles (HTTP 403 otherwise).
        dashboardService.requireAllowed(viewer, dashboardId);
        // Look up the dashboard's embed id.
        String embedId = superset.embeddedId(String.valueOf(dashboardId));

        // Split the full name into first name and the rest, e.g. "Kendell" and "St. Rose".
        String[] nameParts = viewer.fullName().split(" ", 2);
        // The user Superset shows for this guest (it grants no access by itself).
        Map<String, Object> user = Map.of(
                // Username.
                "username", viewer.username(),
                // First name.
                "first_name", nameParts[0],
                // Last name, or empty if there is none.
                "last_name", nameParts.length > 1 ? nameParts[1] : "");
        // The only thing this token may open: this dashboard.
        Map<String, Object> dashboard = Map.of("type", "dashboard", "id", embedId);
        // The RLS rule Superset adds to every query; national users get no rule at all.
        List<Map<String, String>> rlsRules = viewer.rlsClause() == null
                ? List.of()
                : List.of(Map.of("clause", viewer.rlsClause()));

        // Now we will ask Superset for the guest token.
        String token = superset.createGuestToken(Map.of(
                // Who the guest is.
                "user", user,
                // What the guest may open.
                "resources", List.of(dashboard),
                // Which rows the guest may see.
                "rls", rlsRules));

        // Now we will write to the console which user got a token and with which RLS clause.
        log.info("Guest token for {} on dashboard {} with RLS clause: {}", viewer.username(), dashboardId,
                viewer.rlsClause());
        // Send the token, the embed id and Superset's embed address to the frontend.
        return new GuestTokenResponse(token, embedId, settings.embedUrl());
    }

    // GET /api/analytics/builder?page=explore: a link that signs the CPU analyst in to Superset
    // (as her own Superset user) and opens a builder page: "explore" (new chart on the census
    // data), "charts", "dashboards", or "dashboard" with dashboardId (edit one dashboard).
    @GetMapping("/builder")
    public BuilderResponse builder(@RequestParam(defaultValue = "explore") String page,
                                   @RequestParam(required = false) Integer dashboardId, HttpSession session) {
        // Find out who is logged in (HTTP 401 if nobody).
        Viewer viewer = accessService.requireViewer(userSession.requireUsername(session));
        // Only CPU analysts may use the builder (HTTP 403 otherwise).
        AccessService.requireCapability(viewer, AccessService.BUILD);
        // Pick the Superset page to open after signing in.
        String path = switch (page) {
            // A new chart on the census dataset, with Superset's top menu hidden (uiConfig=4).
            case "explore" -> "/explore/?datasource_type=table&datasource_id=" + superset.censusDatasetId()
                    + "&uiConfig=4";
            // The list of saved charts.
            case "charts" -> "/chart/list/";
            // The list of dashboards.
            case "dashboards" -> "/dashboard/list/";
            // One dashboard, to view or edit.
            case "dashboard" -> "/superset/dashboard/" + requireDashboardId(dashboardId) + "/";
            // Anything else is a mistake.
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown builder page: " + page);
        };
        // Now we will make the sign-in token (it works for one minute).
        String token = ssoTokens.createToken(viewer);
        // The link: Superset's sign-in route, with the token and the page to go to next.
        String url = settings.publicUrl() + "/emis/sso?token=" + token
                + "&next=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
        // Send the link and Superset's address to the frontend.
        return new BuilderResponse(url, settings.publicUrl());
    }

    // Returns the dashboard id, or stops with HTTP 400 if it is missing.
    private static int requireDashboardId(Integer dashboardId) {
        // Missing.
        if (dashboardId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give a dashboardId.");
        // Return it.
        return dashboardId;
    }
}
