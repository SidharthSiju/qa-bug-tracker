package com.duc.crqa.testcycle.controller;

import com.duc.crqa.common.exception.ResourceNotFoundException;
import com.duc.crqa.common.util.SortSupport;
import com.duc.crqa.cr.service.ChangeRequestService;
import com.duc.crqa.testcase.service.TestCaseService;
import com.duc.crqa.testcycle.dto.TestCycleForm;
import com.duc.crqa.testcycle.dto.TestCycleSearchCriteria;
import com.duc.crqa.testcycle.entity.TestCycle;
import com.duc.crqa.testcycle.service.TestCycleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/test-cycles")
public class TestCycleController {

    private static final Set<String> SORTABLE = Set.of("cr.crNumber", "cycleNumber", "name", "status",
            "startDate", "endDate");

    private static final String CYCLE_EDIT =
            "hasAnyRole('ADMIN','QA_LEAD','QA_TESTER','DEVELOPER')";
    private static final String LEAD = "hasAnyRole('ADMIN','SA_LEAD','QA_LEAD')";

    private final TestCycleService testCycleService;
    private final TestCaseService testCaseService;
    private final ChangeRequestService changeRequestService;

    public TestCycleController(TestCycleService testCycleService,
                               TestCaseService testCaseService,
                               ChangeRequestService changeRequestService) {
        this.testCycleService = testCycleService;
        this.testCaseService = testCaseService;
        this.changeRequestService = changeRequestService;
    }

    @GetMapping
    public String list(@ModelAttribute TestCycleSearchCriteria criteria,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(defaultValue = "id") String sort,
                       @RequestParam(defaultValue = "desc") String dir,
                       Model model) {
        model.addAttribute("page", testCycleService.search(criteria, PageRequest.of(page, size,
                SortSupport.resolve(sort, dir, SORTABLE, Sort.by(Sort.Direction.DESC, "id")))));
        model.addAttribute("criteria", criteria);
        model.addAttribute("crs", changeRequestService.findAll());
        if (criteria.getCrId() != null) {
            model.addAttribute("cr", changeRequestService.getById(criteria.getCrId()));
        }
        return "testcycle/list";
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize(LEAD)
    public String bulkDelete(@RequestParam(value = "ids", required = false) List<Long> ids,
                             @RequestParam(required = false) Long crId,
                             RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Select at least one test cycle to delete");
        } else {
            int count = testCycleService.deleteAll(ids);
            redirectAttributes.addFlashAttribute("success", count + " test cycle(s) deleted");
        }
        return crId != null ? "redirect:/change-requests/" + crId + "?tab=cycles" : "redirect:/test-cycles";
    }

    @GetMapping("/new")
    @PreAuthorize(CYCLE_EDIT)
    public String newForm(@RequestParam Long crId, Model model) {
        TestCycleForm form = new TestCycleForm();
        form.setCrId(crId);
        model.addAttribute("cycleForm", form);
        model.addAttribute("cr", changeRequestService.getById(crId));
        return "testcycle/create";
    }

    @PostMapping
    @PreAuthorize(CYCLE_EDIT)
    public String create(@Valid @ModelAttribute("cycleForm") TestCycleForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("cr", changeRequestService.getById(form.getCrId()));
            return "testcycle/create";
        }
        TestCycle cycle = testCycleService.create(form);
        redirectAttributes.addFlashAttribute("success", "Test cycle created");
        return "redirect:/test-cycles/" + cycle.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        TestCycle cycle = testCycleService.getById(id);
        model.addAttribute("cycle", cycle);
        model.addAttribute("testCases", testCaseService.listByCycle(id));
        return "testcycle/detail";
    }

    @PostMapping("/{id}/start")
    @PreAuthorize(CYCLE_EDIT)
    public String start(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        testCycleService.start(id);
        redirectAttributes.addFlashAttribute("success", "Test cycle started");
        return "redirect:/test-cycles/" + id;
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(CYCLE_EDIT)
    public String complete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        testCycleService.complete(id);
        redirectAttributes.addFlashAttribute("success", "Test cycle completed");
        return "redirect:/test-cycles/" + id;
    }
}
