package com.duc.crqa.jira.controller;

import com.duc.crqa.jira.dto.JiraImportResult;
import com.duc.crqa.jira.dto.JiraImportRow;
import com.duc.crqa.jira.dto.JiraIssue;
import com.duc.crqa.jira.dto.JiraSearchForm;
import com.duc.crqa.jira.exception.JiraException;
import com.duc.crqa.jira.service.JiraConfigService;
import com.duc.crqa.jira.service.JiraImportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Import from JIRA" for change requests: a search form (opened in its own window), a paginated review table of the
 * issues JIRA returned, and the import of the selected rows.
 *
 * <p>The fetched issues are held in the HTTP session so the review table can be paged and re-rendered without calling
 * JIRA again; the selection is carried across pages by the page links.
 */
@Controller
@RequestMapping("/change-requests/jira-import")
@PreAuthorize("hasAnyRole('ADMIN','CR_MANAGER','SA_LEAD','QA_LEAD')")
public class CrJiraImportController {

    private static final String SESSION_ISSUES = "jiraImportIssues";
    private static final int MAX_PAGE_SIZE = 200;

    private final JiraImportService jiraImportService;
    private final JiraConfigService jiraConfigService;

    public CrJiraImportController(JiraImportService jiraImportService, JiraConfigService jiraConfigService) {
        this.jiraImportService = jiraImportService;
        this.jiraConfigService = jiraConfigService;
    }

    @GetMapping
    public String form(Model model) {
        if (!model.containsAttribute("searchForm")) {
            model.addAttribute("searchForm", new JiraSearchForm());
        }
        addFormOptions(model);
        return "cr/jira-import";
    }

    @PostMapping("/search")
    public String search(@Valid @ModelAttribute("searchForm") JiraSearchForm searchForm,
                         BindingResult bindingResult,
                         Model model,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addFormOptions(model);
            return "cr/jira-import";
        }

        if (jiraConfigService.isClientSide()) {
            // This form is meant to be submitted by the browser's JIRA client; a plain submit (JavaScript switched
            // off) must not quietly fall back to a server-side call that this deployment cannot make.
            model.addAttribute("error", "The JIRA search is set to run in the browser (Administration -> JIRA "
                    + "Configuration). Use \"Search in JIRA from this browser\", and check that JavaScript is enabled.");
            addFormOptions(model);
            return "cr/jira-import";
        }

        List<JiraIssue> issues;
        try {
            issues = jiraImportService.search(searchForm);
        } catch (JiraException ex) {
            model.addAttribute("error", ex.getMessage());
            addFormOptions(model);
            return "cr/jira-import";
        }

        session.setAttribute(SESSION_ISSUES, new ArrayList<>(issues));
        redirectAttributes.addFlashAttribute("success", issues.isEmpty()
                ? "No JIRA issues matched status " + searchForm.getStatus()
                        + " created since " + searchForm.getFromDate()
                : issues.size() + " change request(s) found in JIRA");
        return "redirect:/change-requests/jira-import/results";
    }

    /**
     * The result of a search the <em>browser</em> ran: the user's browser called JIRA (which this server may not be
     * able to reach) and posts the response here, where it is mapped exactly like a server-side response and the
     * normal review/import flow takes over. The credentials used for the call stay in the browser.
     */
    @PostMapping("/client-search")
    public String clientSideSearch(@RequestParam String payload,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) LocalDate fromDate,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        List<JiraIssue> issues;
        try {
            issues = jiraImportService.readIssues(payload);
        } catch (JiraException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/change-requests/jira-import";
        }

        session.setAttribute(SESSION_ISSUES, new ArrayList<>(issues));
        redirectAttributes.addFlashAttribute("success", issues.isEmpty()
                ? "No JIRA issues matched status " + status + " created since " + fromDate
                : issues.size() + " change request(s) found in JIRA (searched from the browser)");
        return "redirect:/change-requests/jira-import/results";
    }

    @GetMapping("/results")
    public String results(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "10") int size,
                          @RequestParam(value = "key", required = false) List<String> selectedKeys,
                          Model model,
                          HttpSession session) {
        List<JiraIssue> issues = issuesFromSession(session);
        if (issues == null) {
            return expiredSearch(model);
        }
        renderResults(model, issues, page, size, selectedKeys);
        return "cr/jira-import-results";
    }

    @PostMapping("/import")
    public String importSelected(@RequestParam(value = "key", required = false) List<String> keys,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        List<JiraIssue> issues = issuesFromSession(session);
        if (issues == null) {
            redirectAttributes.addFlashAttribute("error",
                    "The JIRA search results are no longer available. Run the search again.");
            return "redirect:/change-requests/jira-import";
        }
        String resultsUrl = "redirect:/change-requests/jira-import/results?page=" + Math.max(0, page)
                + "&size=" + Math.max(1, size);
        if (keys == null || keys.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Select at least one change request to import");
            return resultsUrl;
        }

        JiraImportResult result = jiraImportService.importIssues(keys, issues);
        redirectAttributes.addFlashAttribute("importResult", result);
        redirectAttributes.addFlashAttribute(result.failed() == 0 ? "success" : "error",
                "JIRA import finished: " + result.summary());
        return resultsUrl;
    }

    /**
     * Writes the selected rows as a change-request bulk-import CSV: the same columns as {@code cr_upload_template.csv}
     * and the values the import would store, so the file can be re-imported through Change Requests &rarr; Bulk Import.
     */
    @PostMapping("/export")
    public String exportSelected(@RequestParam(value = "key", required = false) List<String> keys,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size,
                                 HttpSession session,
                                 HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) throws IOException {
        String resultsUrl = "redirect:/change-requests/jira-import/results?page=" + Math.max(0, page)
                + "&size=" + Math.max(1, size);
        List<JiraIssue> issues = issuesFromSession(session);
        if (issues == null) {
            redirectAttributes.addFlashAttribute("error",
                    "The JIRA search results are no longer available. Run the search again.");
            return "redirect:/change-requests/jira-import";
        }
        if (keys == null || keys.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Select at least one change request to export");
            return resultsUrl;
        }

        String csv = jiraImportService.bulkImportCsv(jiraImportService.selected(keys, issues));
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"jira-change-requests-" + LocalDate.now() + ".csv\"");
        response.getWriter().write(csv);
        // The response is the download itself, so there is no view to render.
        return null;
    }

    @PostMapping("/clear")
    public String clear(HttpSession session, RedirectAttributes redirectAttributes) {
        session.removeAttribute(SESSION_ISSUES);
        redirectAttributes.addFlashAttribute("success", "JIRA search results cleared");
        return "redirect:/change-requests/jira-import";
    }

    private String expiredSearch(Model model) {
        model.addAttribute("error", "The JIRA search results are no longer available. Run the search again.");
        model.addAttribute("searchForm", new JiraSearchForm());
        addFormOptions(model);
        return "cr/jira-import";
    }

    private void renderResults(Model model, List<JiraIssue> issues, int page, int size, List<String> selectedKeys) {
        int safeSize = size < 1 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        int totalPages = (int) Math.ceil(issues.size() / (double) safeSize);
        int safePage = totalPages == 0 ? 0 : Math.max(0, Math.min(page, totalPages - 1));
        int from = Math.min(safePage * safeSize, issues.size());
        int to = Math.min(from + safeSize, issues.size());

        List<JiraImportRow> rows = issues.subList(from, to).stream()
                .map(jiraImportService::toRow)
                .toList();
        List<String> pageKeys = rows.stream().map(JiraImportRow::storyNumber).toList();

        List<String> selected = selectedKeys == null ? List.of() : selectedKeys.stream().distinct().toList();
        // Keys selected on earlier pages travel with the form as hidden fields; the ones visible on this page are
        // rendered as (checked) checkboxes instead, so unticking a row really removes it from the import.
        List<String> hiddenKeys = selected.stream().filter(key -> !pageKeys.contains(key)).toList();

        Page<JiraImportRow> pageData = new PageImpl<>(rows, PageRequest.of(safePage, safeSize), issues.size());
        model.addAttribute("page", pageData);
        model.addAttribute("totalIssues", issues.size());
        model.addAttribute("existingCount", rows.stream().filter(JiraImportRow::existing).count());
        model.addAttribute("selectedKeys", selected);
        model.addAttribute("hiddenKeys", hiddenKeys);
    }

    @SuppressWarnings("unchecked")
    private List<JiraIssue> issuesFromSession(HttpSession session) {
        Object value = session.getAttribute(SESSION_ISSUES);
        return value instanceof List<?> list ? (List<JiraIssue>) list : null;
    }

    private void addFormOptions(Model model) {
        model.addAttribute("statuses", jiraConfigService.statuses());
        model.addAttribute("jiraEnabled", jiraConfigService.isEnabled());
        model.addAttribute("jiraConfigured", jiraConfigService.isConfigured());
        model.addAttribute("jiraReady", jiraConfigService.isReady());
        model.addAttribute("jqlTemplate", jiraConfigService.jql());
        model.addAttribute("maxResults", jiraConfigService.maxResults());
        model.addAttribute("fieldDocs", JiraImportService.fieldDocs());
        model.addAttribute("clientSide", jiraConfigService.isClientSide());
        model.addAttribute("jiraEndpoint", jiraImportService.searchEndpoint());
        model.addAttribute("jiraFields", String.join(",", jiraImportService.requestFields()));
        model.addAttribute("jiraEmail", jiraConfigService.email());
    }
}
