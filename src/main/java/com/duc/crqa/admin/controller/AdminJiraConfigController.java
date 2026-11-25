package com.duc.crqa.admin.controller;

import com.duc.crqa.jira.service.JiraConfigService;
import com.duc.crqa.jira.service.JiraImportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ADMIN-only screen where the JIRA API URL, credentials, result limit, JQL and fields are maintained.
 */
@Controller
@RequestMapping("/admin/jira-config")
@PreAuthorize("hasRole('ADMIN')")
public class AdminJiraConfigController {

    private final JiraConfigService jiraConfigService;

    public AdminJiraConfigController(JiraConfigService jiraConfigService) {
        this.jiraConfigService = jiraConfigService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("config", jiraConfigService.current());
        model.addAttribute("defaults", jiraConfigService.defaults());
        model.addAttribute("fieldDocs", JiraImportService.fieldDocs());
        model.addAttribute("configured", jiraConfigService.isConfigured());
        model.addAttribute("maxResultsLimit", JiraConfigService.MAX_RESULTS_LIMIT);
        model.addAttribute("timeoutLimit", JiraConfigService.MAX_TIMEOUT_SECONDS);
        return "admin/jira-config";
    }

    @PostMapping
    public String save(@RequestParam(defaultValue = "false") boolean enabled,
                       @RequestParam(defaultValue = "false") boolean clientSide,
                       @RequestParam(required = false) String baseUrl,
                       @RequestParam(required = false) String email,
                       @RequestParam(required = false) String apiToken,
                       @RequestParam(required = false) String caCertificate,
                       @RequestParam(required = false) Integer maxResults,
                       @RequestParam(required = false) Integer timeoutSeconds,
                       @RequestParam(required = false) String jql,
                       @RequestParam(required = false) String fieldList,
                       @RequestParam(required = false) String statusList,
                       RedirectAttributes redirectAttributes) {
        jiraConfigService.update(enabled, clientSide, baseUrl, email, apiToken, caCertificate, maxResults,
                timeoutSeconds, jql, fieldList, statusList);
        redirectAttributes.addFlashAttribute("success", "JIRA configuration saved");
        return "redirect:/admin/jira-config";
    }
}
