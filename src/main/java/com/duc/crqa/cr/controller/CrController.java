package com.duc.crqa.cr.controller;

import com.duc.crqa.bug.dto.BugSearchCriteria;
import com.duc.crqa.admin.service.UiSettingsService;
import com.duc.crqa.bug.service.BugService;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.CrStatus;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.common.enums.TestCaseReviewStage;
import com.duc.crqa.common.enums.TestCaseStatus;
import com.duc.crqa.common.enums.TestCycleStatus;
import com.duc.crqa.common.util.SampleCsv;
import com.duc.crqa.common.util.SortSupport;
import com.duc.crqa.cr.dto.CrForm;
import com.duc.crqa.cr.dto.CrSearchCriteria;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.service.ChangeRequestService;
import com.duc.crqa.cr.service.CrBulkUploadService;
import com.duc.crqa.cr.service.CrWorkflowService;
import com.duc.crqa.cr.service.TestCaseReviewService;
import com.duc.crqa.common.util.SortSupport;
import com.duc.crqa.project.service.ProjectService;
import com.duc.crqa.testcase.dto.TestCaseSearchCriteria;
import com.duc.crqa.testcase.service.TestCaseService;
import com.duc.crqa.testcycle.dto.TestCycleSearchCriteria;
import com.duc.crqa.testcycle.service.TestCycleService;
import com.duc.crqa.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/change-requests")
public class CrController {

    private static final Set<String> SORTABLE = Set.of("crNumber", "crName", "project.projectCode", "priority",
            "complexity", "sprintName", "storyNumber", "saLead.username", "qaLead.username",
            "developmentOwner.username", "status", "plannedEndDate");

    private static final String CR_EDIT = "hasAnyRole('ADMIN','CR_MANAGER','SA_LEAD','QA_LEAD')";
    private static final String TC_REVIEW = "hasAnyRole('ADMIN','QA_LEAD','SA_LEAD')";

    private static final Set<String> ACTIVITY_SORTABLE = Set.of("performedAt", "activityType", "description");
    private static final Set<String> CYCLE_SORTABLE = Set.of("cycleNumber", "name", "status", "startDate", "endDate");
    private static final Set<String> CASE_SORTABLE = Set.of("testCaseNumber", "title", "cycle.cycleNumber",
            "status", "executedBy");
    private static final Set<String> BUG_SORTABLE = Set.of("bugNumber", "title", "severity", "priority", "status",
            "sprint", "defectCategory", "testPhaseFound", "assignedTo.username");

    private final ChangeRequestService changeRequestService;
    private final CrWorkflowService crWorkflowService;
    private final CrBulkUploadService crBulkUploadService;
    private final BugService bugService;
    private final TestCycleService testCycleService;
    private final TestCaseService testCaseService;
    private final TestCaseReviewService testCaseReviewService;
    private final ProjectService projectService;
    private final UserService userService;
    private final UiSettingsService uiSettingsService;

    public CrController(ChangeRequestService changeRequestService,
                        CrWorkflowService crWorkflowService,
                        CrBulkUploadService crBulkUploadService,
                        BugService bugService,
                        TestCycleService testCycleService,
                        TestCaseService testCaseService,
                        TestCaseReviewService testCaseReviewService,
                        ProjectService projectService,
                        UserService userService,
                        UiSettingsService uiSettingsService) {
        this.changeRequestService = changeRequestService;
        this.crWorkflowService = crWorkflowService;
        this.crBulkUploadService = crBulkUploadService;
        this.bugService = bugService;
        this.testCycleService = testCycleService;
        this.testCaseService = testCaseService;
        this.testCaseReviewService = testCaseReviewService;
        this.projectService = projectService;
        this.userService = userService;
        this.uiSettingsService = uiSettingsService;
    }

    @GetMapping
    public String list(@ModelAttribute CrSearchCriteria criteria,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(defaultValue = "createdAt") String sort,
                       @RequestParam(defaultValue = "desc") String dir,
                       Model model) {
        Page<ChangeRequest> result = changeRequestService.search(criteria, PageRequest.of(page, size,
                SortSupport.resolve(sort, dir, SORTABLE, Sort.by(Sort.Direction.DESC, "createdAt"))));
        model.addAttribute("page", result);
        model.addAttribute("criteria", criteria);
        addFormOptions(model);
        return "cr/list";
    }

    @GetMapping("/new")
    @PreAuthorize(CR_EDIT)
    public String newForm(Model model) {
        model.addAttribute("crForm", new CrForm());
        addFormOptions(model);
        return "cr/create";
    }

    @GetMapping("/upload")
    @PreAuthorize(CR_EDIT)
    public String uploadForm() {
        return "cr/upload";
    }

    @PostMapping("/upload")
    @PreAuthorize(CR_EDIT)
    public String upload(@RequestParam("file") MultipartFile file, Model model) {
        try {
            model.addAttribute("result", crBulkUploadService.importFile(file));
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "cr/upload";
    }

    @GetMapping("/upload/template")
    @PreAuthorize(CR_EDIT)
    public void uploadTemplate(HttpServletResponse response) throws IOException {
        SampleCsv.write("samples/cr_upload_template.csv", "cr_upload_template.csv", response);
    }

    @PostMapping
    @PreAuthorize(CR_EDIT)
    public String create(@Valid @ModelAttribute("crForm") CrForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addFormOptions(model);
            return "cr/create";
        }
        ChangeRequest cr;
        try {
            cr = changeRequestService.create(form);
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("crNumber", "duplicate", ex.getMessage());
            addFormOptions(model);
            return "cr/create";
        }
        redirectAttributes.addFlashAttribute("success", "Change request " + cr.getCrNumber() + " created");
        return "redirect:/change-requests/" + cr.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(defaultValue = "overview") String tab,
                         @RequestParam(defaultValue = "") String activitySearch,
                         @RequestParam(defaultValue = "performedAt") String activitySort,
                         @RequestParam(defaultValue = "desc") String activityDir,
                         @RequestParam(defaultValue = "0") int activityPage,
                         @RequestParam(defaultValue = "") String cycleSearch,
                         @RequestParam(required = false) TestCycleStatus cycleStatus,
                         @RequestParam(defaultValue = "cycleNumber") String cycleSort,
                         @RequestParam(defaultValue = "desc") String cycleDir,
                         @RequestParam(defaultValue = "0") int cyclePage,
                         @RequestParam(defaultValue = "") String caseSearch,
                         @RequestParam(required = false) TestCaseStatus caseStatus,
                         @RequestParam(defaultValue = "testCaseNumber") String caseSort,
                         @RequestParam(defaultValue = "asc") String caseDir,
                         @RequestParam(defaultValue = "0") int casePage,
                         @RequestParam(defaultValue = "") String bugSearch,
                         @RequestParam(required = false) BugStatus bugStatus,
                         @RequestParam(required = false) Severity bugSeverity,
                         @RequestParam(defaultValue = "bugNumber") String bugSort,
                         @RequestParam(defaultValue = "asc") String bugDir,
                         @RequestParam(defaultValue = "0") int bugPage,
                         Model model) {
        ChangeRequest cr = changeRequestService.getById(id);
        model.addAttribute("cr", cr);
        model.addAttribute("tab", tab);
        model.addAttribute("statuses", CrStatus.values());

        model.addAttribute("activityPage", changeRequestService.searchActivities(id, activitySearch,
                PageRequest.of(activityPage, uiSettingsService.rowsPerPage(), SortSupport.resolve(activitySort, activityDir,
                        ACTIVITY_SORTABLE, Sort.by(Sort.Direction.DESC, "performedAt")))));

        TestCycleSearchCriteria cycleCriteria = new TestCycleSearchCriteria();
        cycleCriteria.setCrId(id);
        cycleCriteria.setSearch(cycleSearch);
        cycleCriteria.setStatus(cycleStatus);
        model.addAttribute("cyclePage", testCycleService.search(cycleCriteria,
                PageRequest.of(cyclePage, uiSettingsService.rowsPerPage(), SortSupport.resolve(cycleSort, cycleDir,
                        CYCLE_SORTABLE, Sort.by(Sort.Direction.DESC, "cycleNumber")))));

        TestCaseSearchCriteria caseCriteria = new TestCaseSearchCriteria();
        caseCriteria.setCrId(id);
        caseCriteria.setSearch(caseSearch);
        caseCriteria.setStatus(caseStatus);
        model.addAttribute("casePage", testCaseService.search(caseCriteria,
                PageRequest.of(casePage, uiSettingsService.rowsPerPage(), SortSupport.resolve(caseSort, caseDir,
                        CASE_SORTABLE, Sort.by(Sort.Direction.ASC, "testCaseNumber")))));

        BugSearchCriteria bugCriteria = new BugSearchCriteria();
        bugCriteria.setCrId(id);
        bugCriteria.setSearch(bugSearch);
        bugCriteria.setStatus(bugStatus);
        bugCriteria.setSeverity(bugSeverity);
        model.addAttribute("bugPage", bugService.search(bugCriteria,
                PageRequest.of(bugPage, uiSettingsService.rowsPerPage(), SortSupport.resolve(bugSort, bugDir,
                        BUG_SORTABLE, Sort.by(Sort.Direction.ASC, "bugNumber")))));

        model.addAttribute("activitySearch", activitySearch);
        model.addAttribute("activitySort", activitySort);
        model.addAttribute("activityDir", activityDir);
        model.addAttribute("cycleCriteria", cycleCriteria);
        model.addAttribute("cycleSort", cycleSort);
        model.addAttribute("cycleDir", cycleDir);
        model.addAttribute("caseCriteria", caseCriteria);
        model.addAttribute("caseSort", caseSort);
        model.addAttribute("caseDir", caseDir);
        model.addAttribute("bugCriteria", bugCriteria);
        model.addAttribute("bugSort", bugSort);
        model.addAttribute("bugDir", bugDir);

        model.addAttribute("reviewStages", TestCaseReviewStage.values());
        model.addAttribute("selectedReviewStage",
                cr.getTcReviewStatus() != null && cr.getTcReviewStatus().stage() != null
                        ? cr.getTcReviewStatus().stage() : TestCaseReviewStage.QA_LEAD);
        model.addAttribute("hasTestCases", testCaseService.countByCr(cr.getId()) > 0);
        model.addAttribute("canSendReview", testCaseReviewService.canSend(cr));
        model.addAttribute("canConfirmReview", testCaseReviewService.canConfirm(cr));
        model.addAttribute("canReopenReview", testCaseReviewService.canReopen(cr));

        return "cr/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize(CR_EDIT)
    public String editForm(@PathVariable Long id, Model model) {
        ChangeRequest cr = changeRequestService.getById(id);
        model.addAttribute("crForm", toForm(cr));
        model.addAttribute("cr", cr);
        addFormOptions(model);
        return "cr/edit";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize(CR_EDIT)
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("crForm") CrForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("cr", changeRequestService.getById(id));
            addFormOptions(model);
            return "cr/edit";
        }
        ChangeRequest cr;
        try {
            cr = changeRequestService.update(id, form);
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("crNumber", "duplicate", ex.getMessage());
            model.addAttribute("cr", changeRequestService.getById(id));
            addFormOptions(model);
            return "cr/edit";
        }
        redirectAttributes.addFlashAttribute("success", "Change request updated");
        return "redirect:/change-requests/" + cr.getId();
    }

    @PostMapping("/{id}/status")
    @PreAuthorize(CR_EDIT)
    public String transition(@PathVariable Long id,
                             @RequestParam CrStatus target,
                             RedirectAttributes redirectAttributes) {
        crWorkflowService.transition(id, target);
        redirectAttributes.addFlashAttribute("success", "Change request status updated to " + target);
        return "redirect:/change-requests/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize(CR_EDIT)
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        changeRequestService.delete(id);
        redirectAttributes.addFlashAttribute("success",
                "Change request deleted; its change request, test cycles, test cases, bugs and activities were moved to the archive");
        return "redirect:/change-requests";
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize(CR_EDIT)
    public String bulkDelete(@RequestParam(value = "ids", required = false) List<Long> ids,
                             RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Select at least one change request to delete");
        } else {
            int count = changeRequestService.deleteAll(ids);
            redirectAttributes.addFlashAttribute("success", count
                    + " change request(s) deleted; each one's test cycles, test cases, bugs and activities were moved to the archive");
        }
        return "redirect:/change-requests";
    }

    @PostMapping("/{id}/test-case-review/send")
    @PreAuthorize(TC_REVIEW)
    public String sendTestCaseReview(@PathVariable Long id,
                                     @RequestParam TestCaseReviewStage reviewGroup,
                                     RedirectAttributes redirectAttributes) {
        testCaseReviewService.send(id, reviewGroup);
        redirectAttributes.addFlashAttribute("success", "Test case review sent to " + reviewGroup.label());
        return "redirect:/change-requests/" + id + "?tab=cases";
    }

    @PostMapping("/{id}/test-case-review/confirm")
    @PreAuthorize(TC_REVIEW)
    public String confirmTestCaseReview(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        testCaseReviewService.confirm(id);
        redirectAttributes.addFlashAttribute("success", "Test case review confirmed");
        return "redirect:/change-requests/" + id + "?tab=cases";
    }

    @PostMapping("/{id}/test-case-review/reopen")
    @PreAuthorize(TC_REVIEW)
    public String reopenTestCaseReview(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        testCaseReviewService.reopen(id);
        redirectAttributes.addFlashAttribute("success", "Test case review reopened");
        return "redirect:/change-requests/" + id + "?tab=cases";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("projects", projectService.listActive());
        model.addAttribute("developers", userService.findDevelopers());
        model.addAttribute("qaLeads", userService.findQaUsers());
        model.addAttribute("saLeads", userService.findSaUsers());

    }

    private CrForm toForm(ChangeRequest cr) {
        CrForm form = new CrForm();
        form.setCrNumber(cr.getCrNumber());
        form.setCrName(cr.getCrName());
        form.setDescription(cr.getDescription());
        form.setProjectId(cr.getProject() == null ? null : cr.getProject().getId());
        form.setEnvironment(cr.getEnvironment());
        form.setPriority(cr.getPriority());
        form.setComplexity(cr.getComplexity());
        form.setSprintName(cr.getSprintName());
        form.setStoryNumber(cr.getStoryNumber());
        form.setSaLeadId(cr.getSaLead() == null ? null : cr.getSaLead().getId());
        form.setDevelopmentOwnerId(cr.getDevelopmentOwner() == null ? null : cr.getDevelopmentOwner().getId());
        form.setQaLeadId(cr.getQaLead() == null ? null : cr.getQaLead().getId());
        form.setPlannedStartDate(cr.getPlannedStartDate() == null ? null : cr.getPlannedStartDate().toLocalDate());
        form.setPlannedEndDate(cr.getPlannedEndDate() == null ? null : cr.getPlannedEndDate().toLocalDate());
        form.setActualStartDate(cr.getActualStartDate() == null ? null : cr.getActualStartDate().toLocalDate());
        form.setActualEndDate(cr.getActualEndDate() == null ? null : cr.getActualEndDate().toLocalDate());
        form.setDacSubmitDate(cr.getDacSubmitDate() == null ? null : cr.getDacSubmitDate().toLocalDate());
        form.setHldSubmitDate(cr.getHldSubmitDate() == null ? null : cr.getHldSubmitDate().toLocalDate());
        form.setVersion(cr.getVersion());
        return form;
    }
}
