package com.duc.crqa.api.controller;

import com.duc.crqa.api.dto.BugDto;
import com.duc.crqa.bug.dto.BugForm;
import com.duc.crqa.bug.dto.BugSearchCriteria;
import com.duc.crqa.bug.dto.CommentForm;
import com.duc.crqa.bug.dto.RetestForm;
import com.duc.crqa.bug.service.BugService;
import com.duc.crqa.bug.service.BugWorkflowService;
import com.duc.crqa.common.enums.Resolution;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bugs")
public class BugRestController {

    private final BugService bugService;
    private final BugWorkflowService bugWorkflowService;

    public BugRestController(BugService bugService, BugWorkflowService bugWorkflowService) {
        this.bugService = bugService;
        this.bugWorkflowService = bugWorkflowService;
    }

    @GetMapping
    public Page<BugDto> list(@ModelAttribute BugSearchCriteria criteria,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size) {
        return bugService.search(criteria,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reportedAt")))
                .map(BugDto::from);
    }

    @GetMapping("/{id}")
    public BugDto get(@PathVariable Long id) {
        return BugDto.from(bugService.getById(id));
    }

    @PostMapping
    public BugDto create(@RequestBody BugForm form) {
        return BugDto.from(bugService.create(form, List.of()));
    }

    @PutMapping("/{id}")
    public BugDto update(@PathVariable Long id, @RequestBody BugForm form) {
        return BugDto.from(bugService.update(id, form));
    }

    @PostMapping("/{id}/comment")
    public BugDto comment(@PathVariable Long id, @RequestBody CommentForm form) {
        bugService.addComment(id, form);
        return BugDto.from(bugService.getById(id));
    }

    @PostMapping("/{id}/assign")
    public BugDto assign(@PathVariable Long id, @RequestParam Long assignedToId) {
        bugWorkflowService.assign(id, assignedToId);
        return BugDto.from(bugService.getById(id));
    }

    @PostMapping("/{id}/start-analysis")
    public BugDto startAnalysis(@PathVariable Long id) {
        bugWorkflowService.startAnalysis(id);
        return BugDto.from(bugService.getById(id));
    }

    @PostMapping("/{id}/start-development")
    public BugDto startDevelopment(@PathVariable Long id) {
        bugWorkflowService.startDevelopment(id);
        return BugDto.from(bugService.getById(id));
    }

    @PostMapping("/{id}/mark-fixed")
    public BugDto markFixed(@PathVariable Long id) {
        bugWorkflowService.markFixed(id);
        return BugDto.from(bugService.getById(id));
    }

    @PostMapping("/{id}/ready-for-retest")
    public BugDto readyForRetest(@PathVariable Long id) {
        bugWorkflowService.readyForRetest(id);
        return BugDto.from(bugService.getById(id));
    }

    @PostMapping("/{id}/start-retest")
    public BugDto startRetest(@PathVariable Long id) {
        bugWorkflowService.startRetest(id);
        return BugDto.from(bugService.getById(id));
    }

    @PostMapping("/{id}/retest")
    public BugDto retest(@PathVariable Long id, @RequestBody RetestForm form) {
        bugWorkflowService.retest(id, form);
        return BugDto.from(bugService.getById(id));
    }

    @PostMapping("/{id}/reject")
    public BugDto reject(@PathVariable Long id,
                         @RequestParam Resolution resolution,
                         @RequestParam(required = false) String comment) {
        bugWorkflowService.reject(id, resolution, comment);
        return BugDto.from(bugService.getById(id));
    }
}
