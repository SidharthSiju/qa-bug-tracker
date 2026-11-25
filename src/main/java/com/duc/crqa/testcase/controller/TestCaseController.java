package com.duc.crqa.testcase.controller;

import com.duc.crqa.admin.service.UiSettingsService;
import com.duc.crqa.common.enums.TestCaseStatus;
import com.duc.crqa.common.exception.ResourceNotFoundException;
import com.duc.crqa.common.util.SampleCsv;
import com.duc.crqa.common.util.SortSupport;
import com.duc.crqa.cr.service.ChangeRequestService;
import com.duc.crqa.testcase.dto.TestCaseForm;
import com.duc.crqa.testcase.dto.TestCaseSearchCriteria;
import com.duc.crqa.testcase.entity.TestCase;
import com.duc.crqa.testcase.service.TestCaseBulkUploadService;
import com.duc.crqa.testcase.service.TestCaseService;
import com.duc.crqa.testcycle.service.TestCycleService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Set;
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
@RequestMapping("/test-cases")
public class TestCaseController {

    private static final Set<String> SORTABLE = Set.of("testCaseNumber", "title", "cr.crNumber",
            "cycle.cycleNumber", "priority", "status", "executedBy");

    private static final String CASE_EDIT =
            "hasAnyRole('ADMIN','SA_LEAD','QA_LEAD','QA_TESTER','DEVELOPER')";
    private static final String LEAD = "hasAnyRole('ADMIN','SA_LEAD','QA_LEAD')";

    private static final Set<String> HISTORY_SORTABLE = Set.of("performedAt", "action", "oldStatus", "newStatus");

    private final TestCaseService testCaseService;
    private final TestCaseBulkUploadService testCaseBulkUploadService;
    private final TestCycleService testCycleService;
    private final ChangeRequestService changeRequestService;
    private final UiSettingsService uiSettingsService;

    public TestCaseController(TestCaseService testCaseService,
                              TestCaseBulkUploadService testCaseBulkUploadService,
                              TestCycleService testCycleService,
                              ChangeRequestService changeRequestService,
                              UiSettingsService uiSettingsService) {
        this.testCaseService = testCaseService;
        this.testCaseBulkUploadService = testCaseBulkUploadService;
        this.testCycleService = testCycleService;
        this.changeRequestService = changeRequestService;
        this.uiSettingsService = uiSettingsService;
    }

    @GetMapping
    public String list(@ModelAttribute TestCaseSearchCriteria criteria,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(defaultValue = "id") String sort,
                       @RequestParam(defaultValue = "desc") String dir,
                       Model model) {
        model.addAttribute("page", testCaseService.search(criteria, PageRequest.of(page, size,
                SortSupport.resolve(sort, dir, SORTABLE, Sort.by(Sort.Direction.DESC, "id")))));
        model.addAttribute("criteria", criteria);
        model.addAttribute("crs", changeRequestService.findAll());
        if (criteria.getCycleId() != null) {
            model.addAttribute("cycle", testCycleService.getById(criteria.getCycleId()));
        }
        if (criteria.getCrId() != null) {
            model.addAttribute("cr", changeRequestService.getById(criteria.getCrId()));
        }
        return "testcase/list";
    }

    @GetMapping("/upload")
    @PreAuthorize(CASE_EDIT)
    public String uploadForm() {
        return "testcase/upload";
    }

    @PostMapping("/upload")
    @PreAuthorize(CASE_EDIT)
    public String upload(@RequestParam("file") MultipartFile file, Model model) {
        try {
            model.addAttribute("result", testCaseBulkUploadService.importFile(file));
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "testcase/upload";
    }

    @GetMapping("/upload/template")
    @PreAuthorize(CASE_EDIT)
    public void uploadTemplate(HttpServletResponse response) throws IOException {
        SampleCsv.write("samples/test_case_upload_template.csv", "test_case_upload_template.csv", response);
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize(LEAD)
    public String bulkDelete(@RequestParam(value = "ids", required = false) List<Long> ids,
                             @RequestParam(required = false) Long crId,
                             RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Select at least one test case to delete");
        } else {
            int count = testCaseService.deleteAll(ids);
            redirectAttributes.addFlashAttribute("success", count + " test case(s) deleted");
        }
        return crId != null ? "redirect:/change-requests/" + crId + "?tab=cases" : "redirect:/test-cases";
    }

    @PostMapping("/bulk-execute")
    @PreAuthorize(CASE_EDIT)
    public String bulkExecute(@RequestParam(value = "ids", required = false) List<Long> ids,
                              @RequestParam(required = false) TestCaseStatus status,
                              @RequestParam(required = false) String actualResult,
                              @RequestParam(required = false) Long crId,
                              RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Select at least one test case to execute");
        } else if (status == null) {
            redirectAttributes.addFlashAttribute("error", "Choose the status to record for the selected test cases");
        } else {
            int count = testCaseService.executeAll(ids, status, actualResult);
            redirectAttributes.addFlashAttribute("success", count + " test case(s) executed as " + status);
        }
        return crId != null ? "redirect:/change-requests/" + crId + "?tab=cases" : "redirect:/test-cases";
    }

    @GetMapping("/new")
    @PreAuthorize(CASE_EDIT)
    public String newForm(@RequestParam(required = false) Long crId,
                          @RequestParam(required = false) Long cycleId,
                          Model model) {
        TestCaseForm form = new TestCaseForm();
        form.setCrId(crId);
        form.setCycleId(cycleId);
        model.addAttribute("testCaseForm", form);
        if (crId == null) {
            // Opened from the Test Cases list: let the user pick the change request first.
            model.addAttribute("crs", changeRequestService.findAll());
        } else {
            model.addAttribute("cr", changeRequestService.getById(crId));
            model.addAttribute("cycles", testCycleService.listByCr(crId));
            if (cycleId != null) {
                model.addAttribute("cycle", testCycleService.getById(cycleId));
            }
        }
        return "testcase/create";
    }

    @PostMapping
    @PreAuthorize(CASE_EDIT)
    public String create(@Valid @ModelAttribute("testCaseForm") TestCaseForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            if (form.getCrId() == null) {
                model.addAttribute("crs", changeRequestService.findAll());
            } else {
                model.addAttribute("cr", changeRequestService.getById(form.getCrId()));
                model.addAttribute("cycles", testCycleService.listByCr(form.getCrId()));
                if (form.getCycleId() != null) {
                    model.addAttribute("cycle", testCycleService.getById(form.getCycleId()));
                }
            }
            return "testcase/create";
        }
        TestCase testCase = testCaseService.create(form);
        redirectAttributes.addFlashAttribute("success", "Test case " + testCase.getTestCaseNumber() + " created");
        return "redirect:/test-cases/" + testCase.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(defaultValue = "") String historySearch,
                         @RequestParam(defaultValue = "performedAt") String historySort,
                         @RequestParam(defaultValue = "desc") String historyDir,
                         @RequestParam(defaultValue = "0") int historyPage,
                         Model model) {
        model.addAttribute("testCase", testCaseService.getById(id));
        model.addAttribute("statuses", TestCaseStatus.values());
        model.addAttribute("historyPage", testCaseService.searchHistory(id, historySearch,
                PageRequest.of(historyPage, uiSettingsService.rowsPerPage(), SortSupport.resolve(historySort, historyDir,
                        HISTORY_SORTABLE, Sort.by(Sort.Direction.DESC, "performedAt")))));
        model.addAttribute("historySearch", historySearch);
        model.addAttribute("historySort", historySort);
        model.addAttribute("historyDir", historyDir);
        return "testcase/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize(CASE_EDIT)
    public String editForm(@PathVariable Long id, Model model) {
        TestCase testCase = testCaseService.getById(id);
        model.addAttribute("testCaseForm", toForm(testCase));
        model.addAttribute("testCase", testCase);
        model.addAttribute("cr", testCase.getCr());
        model.addAttribute("cycles", testCycleService.listByCr(testCase.getCr().getId()));
        if (testCase.getCycle() != null) {
            model.addAttribute("cycle", testCase.getCycle());
        }
        return "testcase/edit";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize(CASE_EDIT)
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("testCaseForm") TestCaseForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            TestCase testCase = testCaseService.getById(id);
            model.addAttribute("testCase", testCase);
            model.addAttribute("cr", testCase.getCr());
            model.addAttribute("cycles", testCycleService.listByCr(testCase.getCr().getId()));
            if (form.getCycleId() != null) {
                model.addAttribute("cycle", testCycleService.getById(form.getCycleId()));
            }
            return "testcase/edit";
        }
        testCaseService.update(id, form);
        redirectAttributes.addFlashAttribute("success", "Test case updated");
        return "redirect:/test-cases/" + id;
    }

    private TestCaseForm toForm(TestCase testCase) {
        TestCaseForm form = new TestCaseForm();
        form.setCrId(testCase.getCr() == null ? null : testCase.getCr().getId());
        form.setCycleId(testCase.getCycle() == null ? null : testCase.getCycle().getId());
        form.setTitle(testCase.getTitle());
        form.setDescription(testCase.getDescription());
        form.setPrecondition(testCase.getPrecondition());
        form.setTestSteps(testCase.getTestSteps());
        form.setExpectedResult(testCase.getExpectedResult());
        form.setPriority(testCase.getPriority());
        form.setLabels(testCase.getLabels());
        form.setIssueType(testCase.getIssueType());
        form.setIssueLink(testCase.getIssueLink());
        return form;
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize(CASE_EDIT)
    public String execute(@PathVariable Long id,
                          @RequestParam TestCaseStatus status,
                          @RequestParam(required = false) String actualResult,
                          RedirectAttributes redirectAttributes) {
        TestCase testCase = testCaseService.execute(id, status, actualResult);
        if (status == TestCaseStatus.FAIL) {
            redirectAttributes.addFlashAttribute("success",
                    "Test case failed - raise a bug to track the defect");
            return "redirect:/bugs/new?crId=" + testCase.getCr().getId() + "&testCaseId=" + testCase.getId();
        }
        redirectAttributes.addFlashAttribute("success", "Test case execution recorded");
        return "redirect:/test-cases/" + id;
    }
}
