// Controllers: they answer the browser's HTTP requests and send back JSON.
package com.emis.analytics.controller;

// Imports: the login session, Java collections and Spring's SQL helper and web annotations.
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
// Our own classes used here.
import com.emis.analytics.dto.request.FieldInput;
import com.emis.analytics.dto.request.FormInput;
import com.emis.analytics.dto.request.KpiInput;
import com.emis.analytics.dto.request.PublishRequest;
import com.emis.analytics.model.DashboardPublication;
import com.emis.analytics.model.Field;
import com.emis.analytics.model.FormDetail;
import com.emis.analytics.model.FormSummary;
import com.emis.analytics.model.KpiDetail;
import com.emis.analytics.model.Viewer;
import com.emis.analytics.service.AccessService;
import com.emis.analytics.service.AnalyticsViewService;
import com.emis.analytics.service.DashboardService;
import com.emis.analytics.service.FieldService;
import com.emis.analytics.service.FormService;
import com.emis.analytics.service.KpiService;
import com.emis.analytics.service.UserSession;

// The CPU analyst's set-up screens: KPI registry, field library, form builder and dashboard access.
@RestController
// Every URL in this class starts with /api/admin.
@RequestMapping("/api/admin")
public class AdminController {

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    // Works out who the logged-in user is and what they may do.
    private final AccessService accessService;
    // Knows who is logged in.
    private final UserSession userSession;
    // The KPI registry.
    private final KpiService kpiService;
    // The field library.
    private final FieldService fieldService;
    // The forms.
    private final FormService formService;
    // Dashboard publishing.
    private final DashboardService dashboardService;
    // Rebuilds the analytics view and syncs Superset.
    private final AnalyticsViewService analyticsView;
    // Spring's helper for running SQL (for the school and district lists).
    private final JdbcTemplate jdbc;

    // Spring passes all the helpers in when it creates the controller.
    public AdminController(AccessService accessService, UserSession userSession, KpiService kpiService,
                           FieldService fieldService, FormService formService, DashboardService dashboardService,
                           AnalyticsViewService analyticsView, JdbcTemplate jdbc) {
        // Keep the access service.
        this.accessService = accessService;
        // Keep the session helper.
        this.userSession = userSession;
        // Keep the KPI service.
        this.kpiService = kpiService;
        // Keep the field service.
        this.fieldService = fieldService;
        // Keep the form service.
        this.formService = formService;
        // Keep the dashboard service.
        this.dashboardService = dashboardService;
        // Keep the view builder.
        this.analyticsView = analyticsView;
        // Keep the SQL helper.
        this.jdbc = jdbc;
    }

    // GET /api/admin/kpis: every KPI with its fields and scope.
    @GetMapping("/kpis")
    public List<KpiDetail> kpis(HttpSession session) {
        // Only the CPU analyst (HTTP 401 / 403 otherwise).
        requireAdmin(session);
        // Now we will return the KPIs.
        return kpiService.listAll();
    }

    // POST /api/admin/kpis: creates a KPI.
    @PostMapping("/kpis")
    public KpiDetail createKpi(@RequestBody KpiInput input, HttpSession session) {
        // Only the CPU analyst.
        requireAdmin(session);
        // Now we will save the new KPI.
        KpiDetail saved = kpiService.save(null, input);
        // Now we will log the next step.
        log.info("KPI {} created; updating Superset's formulas", saved.code());
        // Update Superset's KPI metrics.
        analyticsView.syncSuperset();
        // Return the saved KPI.
        return saved;
    }

    // PUT /api/admin/kpis/{id}: updates a KPI.
    @PutMapping("/kpis/{id}")
    public KpiDetail updateKpi(@PathVariable UUID id, @RequestBody KpiInput input,
                                          HttpSession session) {
        // Only the CPU analyst.
        requireAdmin(session);
        // Now we will save the KPI.
        KpiDetail saved = kpiService.save(id, input);
        // Now we will log the next step.
        log.info("KPI {} updated; updating Superset's formulas", saved.code());
        // Update Superset's KPI metrics.
        analyticsView.syncSuperset();
        // Return the saved KPI.
        return saved;
    }

    // GET /api/admin/fields: every field with the KPIs that use it.
    @GetMapping("/fields")
    public List<Field> fields(HttpSession session) {
        // Only the CPU analyst.
        requireAdmin(session);
        // Now we will return the fields.
        return fieldService.listAll();
    }

    // POST /api/admin/fields: creates a field.
    @PostMapping("/fields")
    public Field createField(@RequestBody FieldInput input, HttpSession session) {
        // Only the CPU analyst.
        requireAdmin(session);
        // Now we will save the new field.
        Field saved = fieldService.save(null, input);
        // A number field is a column of the analytics view, so rebuild it and tell Superset.
        if (saved.fieldType().equals("NUMBER")) {
            // Now we will log the next step.
            log.info("Number field {} created; rebuilding the analytics view and updating Superset", saved.code());
            // Rebuild and sync.
            analyticsView.rebuildAndSync();
        }
        // Return the saved field.
        return saved;
    }

    // PUT /api/admin/fields/{id}: updates a field.
    @PutMapping("/fields/{id}")
    public Field updateField(@PathVariable UUID id, @RequestBody FieldInput input,
                                          HttpSession session) {
        // Only the CPU analyst.
        requireAdmin(session);
        // Now we will save the field.
        Field saved = fieldService.save(id, input);
        // Its label is a column label in Superset, so tell Superset.
        if (saved.fieldType().equals("NUMBER")) {
            // Now we will log the next step.
            log.info("Number field {} updated; updating its label in Superset", saved.code());
            // Sync.
            analyticsView.syncSuperset();
        }
        // Return the saved field.
        return saved;
    }

    // GET /api/admin/forms: every form with its KPIs.
    @GetMapping("/forms")
    public List<FormSummary> forms(HttpSession session) {
        // Only the CPU analyst.
        requireAdmin(session);
        // Now we will return the forms.
        return formService.listAll();
    }

    // GET /api/admin/forms/{id}: one form with its Form.io definition.
    @GetMapping("/forms/{id}")
    public FormDetail form(@PathVariable UUID id, HttpSession session) {
        // Only the CPU analyst.
        requireAdmin(session);
        // Now we will return the form.
        return formService.get(id);
    }

    // POST /api/admin/forms: creates a form.
    @PostMapping("/forms")
    public FormDetail createForm(@RequestBody FormInput input, HttpSession session) {
        // Only the CPU analyst.
        requireAdmin(session);
        // Now we will save the new form and return it.
        return formService.save(null, input);
    }

    // PUT /api/admin/forms/{id}: updates a form.
    @PutMapping("/forms/{id}")
    public FormDetail updateForm(@PathVariable UUID id, @RequestBody FormInput input,
                                             HttpSession session) {
        // Only the CPU analyst.
        requireAdmin(session);
        // Now we will save the form and return it.
        return formService.save(id, input);
    }

    // GET /api/admin/dashboards: every Superset dashboard with the roles it is published to.
    @GetMapping("/dashboards")
    public List<Map<String, Object>> dashboards(HttpSession session) {
        // Only the CPU analyst.
        requireAdmin(session);
        // Now we will return the dashboards.
        return dashboardService.listAll();
    }

    // PUT /api/admin/dashboards/{id} with {"title": "...", "roles": [...]}: publishes a dashboard.
    @PutMapping("/dashboards/{id}")
    public DashboardPublication publish(@PathVariable int id, @RequestBody PublishRequest request,
                                                HttpSession session) {
        // Only the CPU analyst.
        requireAdmin(session);
        // Now we will log the publishing.
        log.info("Publishing dashboard {} (\"{}\") to roles {}", id, request.title(), request.roles());
        // Now we will publish it and return the result.
        return dashboardService.publish(id, request.title(), request.roles());
    }

    // GET /api/admin/places: districts and schools, for the KPI scope picker.
    @GetMapping("/places")
    public Map<String, Object> places(HttpSession session) {
        // Only the CPU analyst.
        requireAdmin(session);
        // Now we will read the districts.
        List<Map<String, Object>> districts = jdbc.queryForList("SELECT code, name FROM district ORDER BY code");
        // Now we will read the active schools.
        List<Map<String, Object>> schools = jdbc.queryForList("""
                SELECT id, name, type, level, district_code AS "districtCode"
                FROM school WHERE is_active ORDER BY name
                """);
        // The school types (fixed by the database).
        List<String> schoolTypes = List.of("Government", "Government-Assisted", "Private");
        // Return all three lists.
        return Map.of("districts", districts, "schools", schools, "schoolTypes", schoolTypes);
    }

    // Stops the request unless a CPU analyst is logged in (HTTP 401 if nobody, 403 otherwise).
    private void requireAdmin(HttpSession session) {
        // Find out who is logged in.
        Viewer viewer = accessService.requireViewer(userSession.requireUsername(session));
        // They must be allowed to manage KPIs.
        AccessService.requireCapability(viewer, AccessService.MANAGE_KPIS);
        // Now we will log that the check passed.
        log.debug("requireAdmin(): {} may manage KPIs", viewer.username());
    }
}
