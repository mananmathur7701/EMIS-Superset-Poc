// Controllers: they answer the browser's HTTP requests and send back JSON.
package com.emis.analytics.controller;

// Imports: the login session, Java collections and Spring's web annotations.
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
// Our own classes used here.
import com.emis.analytics.dto.request.ReviewRequest;
import com.emis.analytics.dto.request.SaveReturnRequest;
import com.emis.analytics.model.ReturnDetail;
import com.emis.analytics.model.ReturnSummary;
import com.emis.analytics.model.Viewer;
import com.emis.analytics.service.AccessService;
import com.emis.analytics.service.ReturnService;
import com.emis.analytics.service.UserSession;

// Filling in forms (registrars) and reviewing them (principals, DEOs and the CPU analyst).
@RestController
// Every URL in this class starts with /api/returns.
@RequestMapping("/api/returns")
public class ReturnController {

    // Logger for writing messages to the console and log files.
    private static final Logger log = LoggerFactory.getLogger(ReturnController.class);

    // Works out who the logged-in user is and what they may do.
    private final AccessService accessService;
    // Knows who is logged in.
    private final UserSession userSession;
    // The returns and their review flow.
    private final ReturnService returnService;

    // Spring passes the helpers in when it creates the controller.
    public ReturnController(AccessService accessService, UserSession userSession, ReturnService returnService) {
        // Keep the access service.
        this.accessService = accessService;
        // Keep the session helper.
        this.userSession = userSession;
        // Keep the return service.
        this.returnService = returnService;
    }

    // GET /api/returns/mine: the registrar's forms for this year, with their status.
    @GetMapping("/mine")
    public List<ReturnSummary> mine(HttpSession session) {
        // Only registrars (HTTP 401 / 403 otherwise).
        Viewer viewer = require(session, AccessService.FILL_FORMS);
        // Now we will return their list.
        return returnService.myForms(viewer);
    }

    // GET /api/returns/form/{formId}/school/{schoolId}: the registrar opens a form for their school.
    @GetMapping("/form/{formId}/school/{schoolId}")
    public ReturnDetail open(@PathVariable UUID formId, @PathVariable UUID schoolId,
                                           HttpSession session) {
        // Only registrars.
        Viewer viewer = require(session, AccessService.FILL_FORMS);
        // Now we will return the form with the saved answers.
        return returnService.openForRegistrar(viewer, formId, schoolId);
    }

    // PUT /api/returns/form/{formId}/school/{schoolId} with {"data": {...}, "submit": false}: saves
    // the answers, and with submit=true sends the return to the principal.
    @PutMapping("/form/{formId}/school/{schoolId}")
    public ReturnDetail save(@PathVariable UUID formId, @PathVariable UUID schoolId,
                                           @RequestBody SaveReturnRequest request, HttpSession session) {
        // Only registrars.
        Viewer viewer = require(session, AccessService.FILL_FORMS);
        // Now we will save (and maybe submit) and return the result.
        return returnService.save(viewer, formId, schoolId, request.data() == null ? Map.of() : request.data(),
                request.submit());
    }

    // GET /api/returns/reviews: this year's returns the reviewer may see, waiting ones first.
    @GetMapping("/reviews")
    public List<ReturnSummary> reviews(HttpSession session) {
        // Only reviewers.
        Viewer viewer = require(session, AccessService.REVIEW);
        // Now we will return their list.
        return returnService.reviewList(viewer);
    }

    // GET /api/returns/{id}: one return, read-only, with its history.
    @GetMapping("/{id}")
    public ReturnDetail one(@PathVariable UUID id, HttpSession session) {
        // Only reviewers.
        Viewer viewer = require(session, AccessService.REVIEW);
        // Now we will return it.
        return returnService.openById(viewer, id);
    }

    // POST /api/returns/{id}/review with {"action": "APPROVE" | "RETURN", "comment": "..."}.
    @PostMapping("/{id}/review")
    public ReturnDetail review(@PathVariable UUID id, @RequestBody ReviewRequest request,
                                             HttpSession session) {
        // Only reviewers.
        Viewer viewer = require(session, AccessService.REVIEW);
        // Now we will approve or send back, and return the updated return.
        return returnService.review(viewer, id, request.action(), request.comment());
    }

    // Returns the logged-in user if they have the capability (HTTP 401 / 403 otherwise).
    private Viewer require(HttpSession session, String capability) {
        // Find out who is logged in.
        Viewer viewer = accessService.requireViewer(userSession.requireUsername(session));
        // Check the capability.
        AccessService.requireCapability(viewer, capability);
        // Now we will log that the check passed.
        log.debug("require(): {} has {}", viewer.username(), capability);
        // Return them.
        return viewer;
    }
}
