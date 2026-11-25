package com.duc.crqa.api.controller;

import com.duc.crqa.api.dto.TestCaseDto;
import com.duc.crqa.common.enums.TestCaseStatus;
import com.duc.crqa.testcase.dto.TestCaseForm;
import com.duc.crqa.testcase.service.TestCaseService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test-cases")
public class TestCaseRestController {

    private final TestCaseService testCaseService;

    public TestCaseRestController(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    @GetMapping
    public List<TestCaseDto> list(@RequestParam(required = false) Long crId,
                                  @RequestParam(required = false) Long cycleId) {
        if (cycleId != null) {
            return testCaseService.listByCycle(cycleId).stream().map(TestCaseDto::from).toList();
        }
        if (crId != null) {
            return testCaseService.listByCr(crId).stream().map(TestCaseDto::from).toList();
        }
        return testCaseService.listAll().stream().map(TestCaseDto::from).toList();
    }

    @GetMapping("/{id}")
    public TestCaseDto get(@PathVariable Long id) {
        return TestCaseDto.from(testCaseService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SA_LEAD','QA_LEAD','QA_TESTER')")
    public TestCaseDto create(@RequestBody TestCaseForm form) {
        return TestCaseDto.from(testCaseService.create(form));
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAnyRole('ADMIN','SA_LEAD','QA_LEAD','QA_TESTER')")
    public TestCaseDto execute(@PathVariable Long id,
                               @RequestParam TestCaseStatus status,
                               @RequestParam(required = false) String actualResult) {
        testCaseService.execute(id, status, actualResult);
        return TestCaseDto.from(testCaseService.getById(id));
    }
}
