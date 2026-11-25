package com.duc.crqa.bug.controller;

import com.duc.crqa.admin.service.UiSettingsService;
import com.duc.crqa.attachment.service.AttachmentStorageService;
import com.duc.crqa.bug.dto.AssignForm;
import com.duc.crqa.bug.dto.BugForm;
import com.duc.crqa.bug.dto.BugSearchCriteria;
import com.duc.crqa.bug.dto.CommentForm;
import com.duc.crqa.bug.dto.RetestForm;
import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.bug.entity.BugAttachment;
import com.duc.crqa.bug.service.BugBulkUploadService;
import com.duc.crqa.bug.service.BugService;
import com.duc.crqa.bug.service.BugWorkflowService;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.CrPriority;
import com.duc.crqa.common.enums.DefectCategory;
import com.duc.crqa.common.enums.Priority;
import com.duc.crqa.common.enums.Resolution;
import com.duc.crqa.common.enums.TestPhase;
import com.duc.crqa.common.util.SampleCsv;
import com.duc.crqa.common.util.SortSupport;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.service.ChangeRequestService;
import com.duc.crqa.security.CurrentUserService;
import com.duc.crqa.testcase.service.TestCaseService;
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
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/bugs")
public class BugController {

    private static final Set<String> SORTABLE = Set.of("bugNumber", "title", "defectCategory", "testPhaseFound",
            "sprint", "severity", "priority", "status", "cr.crNumber", "reportedBy.username",
            "assignedTo.username", "reportedAt");

    private static final String BUG_EDIT =
            "hasAnyRole('ADMIN','SA_LEAD','QA_LEAD','QA_TESTER','DEVELOPER')";
    private static final String LEAD = "hasAnyRole('ADMIN','SA_LEAD','QA_LEAD')";

    private static final Set<String> HISTORY_SORTABLE = Set.of("performedAt", "action", "oldStatus", "newStatus");

    private final BugService bugService;
    private final BugWorkflowService bugWorkflowService;
    private final BugBulkUploadService bugBulkUploadService;
    private final ChangeRequestService changeRequestService;
    private final TestCaseService testCaseService;
    private final UserService userService;
    private final CurrentUserService currentUserService;
    private final AttachmentStorageService attachmentStorageService;
    private final UiSettingsService uiSettingsService;

    public BugController(BugService bugService,
                         BugWorkflowService bugWorkflowService,
                         BugBulkUploadService bugBulkUploadService,
                         ChangeRequestService changeRequestService,
                         TestCaseService testCaseService,
                         UserService userService,
                         CurrentUserService currentUserService,
                         AttachmentStorageService attachmentStorageService,
                         UiSettingsService uiSettingsService) {
        this.bugService = bugService;
        this.bugWorkflowService = bugWorkflowService;
        this.bugBulkUploadService = bugBulkUploadService;
        this.changeRequestService = changeRequestService;
        this.testCaseService = testCaseService;
        this.userService = userService;
        this.currentUserService = currentUserService;
        this.attachmentStorageService = attachmentStorageService;
        this.uiSettingsService = uiSettingsService;
    }

    @GetMapping
    public String list(@ModelAttribute BugSearchCriteria criteria,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(defaultValue = "reportedAt") String sort,
                       @RequestParam(defaultValue = "desc") String dir,
                       Model model) {
        Page<Bug> result = bugService.search(criteria, PageRequest.of(page, size,
                SortSupport.resolve(sort, dir, SORTABLE, Sort.by(Sort.Direction.DESC, "reportedAt"))));
        model.addAttribute("page", result);
        model.addAttribute("criteria", criteria);
        model.addAttribute("slaViews", bugService.slaViews(result.getContent()));
        addFormOptions(model, null);
        return "bug/list";
    }

    @GetMapping("/upload")
    @PreAuthorize(BUG_EDIT)
    public String uploadForm() {
        return "bug/upload";
    }

    @PostMapping("/upload")
    @PreAuthorize(BUG_EDIT)
    public String upload(@RequestParam("file") MultipartFile file, Model model) {
        try {
            model.addAttribute("result", bugBulkUploadService.importFile(file));
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "bug/upload";
    }

    @GetMapping("/upload/template")
    @PreAuthorize(BUG_EDIT)
    public void uploadTemplate(HttpServletResponse response) throws IOException {
        SampleCsv.write("samples/bug_upload_template.csv", "bug_upload_template.csv", response);
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize(LEAD)
    public String bulkDelete(@RequestParam(value = "ids", required = false) List<Long> ids,
                             @RequestParam(required = false) Long crId,
                             RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Select at least one bug to delete");
        } else {
            int count = bugService.deleteAll(ids);
            redirectAttributes.addFlashAttribute("success", count + " bug(s) deleted");
        }
        return crId != null ? "redirect:/change-requests/" + crId + "?tab=bugs" : "redirect:/bugs";
    }

    @GetMapping("/new")
    @PreAuthorize(BUG_EDIT)
    public String newForm(@RequestParam(required = false) Long crId,
                          @RequestParam(required = false) Long testCaseId,
                          Model model) {
        Long effectiveCrId = crId;
        if (effectiveCrId == null && testCaseId != null) {
            effectiveCrId = testCaseService.getById(testCaseId).getCr().getId();
        }

        BugForm form = new BugForm();
        form.setCrId(effectiveCrId);
        form.setTestCaseId(testCaseId);
        if (effectiveCrId != null) {
            ChangeRequest cr = changeRequestService.getById(effectiveCrId);
            form.setPriority(mapPriority(cr.getPriority()));
            form.setEnvironment("SIT");
            form.setSprint(cr.getSprintName());
            form.setTestPhaseFound(TestPhase.SIT);
        }
        model.addAttribute("bugForm", form);
        addFormOptions(model, effectiveCrId);
        return "bug/create";
    }

    @PostMapping
    @PreAuthorize(BUG_EDIT)
    public String create(@Valid @ModelAttribute("bugForm") BugForm form,
                         BindingResult bindingResult,
                         @RequestParam(value = "files", required = false) List<MultipartFile> files,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addFormOptions(model, form.getCrId());
            return "bug/create";
        }
        Bug bug = bugService.create(form, files);
        redirectAttributes.addFlashAttribute("success", "Bug " + bug.getBugNumber() + " created");
        return "redirect:/bugs/" + bug.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(defaultValue = "details") String tab,
                         @RequestParam(defaultValue = "") String historySearch,
                         @RequestParam(defaultValue = "performedAt") String historySort,
                         @RequestParam(defaultValue = "desc") String historyDir,
                         @RequestParam(defaultValue = "0") int historyPage,
                         Model model) {
        Bug bug = bugService.getById(id);
        model.addAttribute("bug", bug);
        model.addAttribute("comments", bugService.comments(id));
        model.addAttribute("attachments", bugService.attachments(id));
        model.addAttribute("historyPage", bugService.searchHistory(id, historySearch,
                PageRequest.of(historyPage, uiSettingsService.rowsPerPage(), SortSupport.resolve(historySort, historyDir,
                        HISTORY_SORTABLE, Sort.by(Sort.Direction.DESC, "performedAt")))));
        model.addAttribute("tab", tab);
        model.addAttribute("historySearch", historySearch);
        model.addAttribute("historySort", historySort);
        model.addAttribute("historyDir", historyDir);
        model.addAttribute("commentForm", new CommentForm());
        model.addAttribute("assignForm", new AssignForm());
        model.addAttribute("retestForm", new RetestForm());
        model.addAttribute("developers", userService.findDevelopers());
        model.addAttribute("qaUsers", userService.findQaUsers());
        model.addAttribute("statuses", BugStatus.values());
        return "bug/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize(BUG_EDIT)
    public String editForm(@PathVariable Long id, Model model) {
        Bug bug = bugService.getById(id);
        model.addAttribute("bugForm", toForm(bug));
        model.addAttribute("bug", bug);
        addFormOptions(model, bug.getCr() == null ? null : bug.getCr().getId());
        return "bug/edit";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize(BUG_EDIT)
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("bugForm") BugForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("bug", bugService.getById(id));
            addFormOptions(model, form.getCrId());
            return "bug/edit";
        }
        bugService.update(id, form);
        redirectAttributes.addFlashAttribute("success", "Bug updated");
        return "redirect:/bugs/" + id;
    }

    @PostMapping("/{id}/comment")
    @PreAuthorize(BUG_EDIT)
    public String comment(@PathVariable Long id,
                          @Valid @ModelAttribute("commentForm") CommentForm form,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Comment cannot be blank");
            return "redirect:/bugs/" + id;
        }
        bugService.addComment(id, form);
        redirectAttributes.addFlashAttribute("success", "Comment added");
        return "redirect:/bugs/" + id;
    }

    @PostMapping("/{id}/assign")
    public String assign(@PathVariable Long id,
                         @ModelAttribute AssignForm form,
                         RedirectAttributes redirectAttributes) {
        if (form.getAssignedToId() == null) {
            redirectAttributes.addFlashAttribute("error", "Assignee is required");
            return "redirect:/bugs/" + id;
        }
        bugWorkflowService.assign(id, form.getAssignedToId());
        redirectAttributes.addFlashAttribute("success", "Bug assigned");
        return "redirect:/bugs/" + id;
    }

    @PostMapping("/{id}/start-analysis")
    public String startAnalysis(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        bugWorkflowService.startAnalysis(id);
        redirectAttributes.addFlashAttribute("success", "Analysis started");
        return "redirect:/bugs/" + id;
    }

    @PostMapping("/{id}/start-development")
    public String startDevelopment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        bugWorkflowService.startDevelopment(id);
        redirectAttributes.addFlashAttribute("success", "Development started");
        return "redirect:/bugs/" + id;
    }

    @PostMapping("/{id}/mark-fixed")
    public String markFixed(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        bugWorkflowService.markFixed(id);
        redirectAttributes.addFlashAttribute("success", "Bug marked as fixed");
        return "redirect:/bugs/" + id;
    }

    @PostMapping("/{id}/ready-for-retest")
    public String readyForRetest(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        bugWorkflowService.readyForRetest(id);
        redirectAttributes.addFlashAttribute("success", "Bug submitted for retest");
        return "redirect:/bugs/" + id;
    }

    @PostMapping("/{id}/start-retest")
    public String startRetest(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        bugWorkflowService.startRetest(id);
        redirectAttributes.addFlashAttribute("success", "Retesting started");
        return "redirect:/bugs/" + id;
    }

    @PostMapping("/{id}/retest")
    public String retest(@PathVariable Long id,
                         @ModelAttribute RetestForm form,
                         RedirectAttributes redirectAttributes) {
        bugWorkflowService.retest(id, form);
        redirectAttributes.addFlashAttribute("success", "Retest result recorded");
        return "redirect:/bugs/" + id;
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam Resolution resolution,
                         @RequestParam(required = false) String comment,
                         RedirectAttributes redirectAttributes) {
        bugWorkflowService.reject(id, resolution, comment);
        redirectAttributes.addFlashAttribute("success", "Bug rejected");
        return "redirect:/bugs/" + id;
    }

    @PostMapping("/{id}/attachment")
    @PreAuthorize(BUG_EDIT)
    public String uploadAttachment(@PathVariable Long id,
                                   @RequestParam("file") MultipartFile file,
                                   RedirectAttributes redirectAttributes) {
        Bug bug = bugService.getById(id);
        try {
            BugAttachment attachment = attachmentStorageService.store(file, bug,
                    currentUserService.currentUserId());
            redirectAttributes.addFlashAttribute("success", "Attachment " + attachment.getFileName() + " uploaded");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/bugs/" + id;
    }

    private void addFormOptions(Model model, Long crId) {
        model.addAttribute("crs", changeRequestService.findAll());
        model.addAttribute("testCases", crId == null ? List.of() : testCaseService.listByCr(crId));
        model.addAttribute("developers", userService.findDevelopers());
        model.addAttribute("qaUsers", userService.findQaUsers());
        model.addAttribute("statuses", BugStatus.values());
        model.addAttribute("defectCategories", DefectCategory.values());
        model.addAttribute("testPhases", TestPhase.values());
    }

    private Priority mapPriority(CrPriority crPriority) {
        if (crPriority == null) {
            return Priority.P2;
        }
        return switch (crPriority) {
            case HIGH -> Priority.P1;
            case MEDIUM -> Priority.P2;
            case LOW -> Priority.P3;
        };
    }

    private BugForm toForm(Bug bug) {
        BugForm form = new BugForm();
        form.setCrId(bug.getCr() == null ? null : bug.getCr().getId());
        form.setTestCaseId(bug.getTestCase() == null ? null : bug.getTestCase().getId());
        form.setTitle(bug.getTitle());
        form.setDescription(bug.getDescription());
        form.setStepsToReproduce(bug.getStepsToReproduce());
        form.setExpectedResult(bug.getExpectedResult());
        form.setActualResult(bug.getActualResult());
        form.setSeverity(bug.getSeverity());
        form.setPriority(bug.getPriority());
        form.setEnvironment(bug.getEnvironment());
        form.setApplication(bug.getApplication());
        form.setModule(bug.getModule());
        form.setSprint(bug.getSprint());
        form.setDefectCategory(bug.getDefectCategory());
        form.setTestPhaseFound(bug.getTestPhaseFound());
        form.setExternalDefectId(bug.getExternalDefectId());
        form.setVersion(bug.getVersion());
        return form;
    }
}
