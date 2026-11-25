package com.duc.crqa.api.controller;

import com.duc.crqa.api.dto.CrDto;
import com.duc.crqa.common.enums.CrStatus;
import com.duc.crqa.cr.dto.CrForm;
import com.duc.crqa.cr.dto.CrSearchCriteria;
import com.duc.crqa.cr.service.ChangeRequestService;
import com.duc.crqa.cr.service.CrWorkflowService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/change-requests")
public class CrRestController {

    private final ChangeRequestService changeRequestService;
    private final CrWorkflowService crWorkflowService;

    public CrRestController(ChangeRequestService changeRequestService,
                            CrWorkflowService crWorkflowService) {
        this.changeRequestService = changeRequestService;
        this.crWorkflowService = crWorkflowService;
    }

    @GetMapping
    public Page<CrDto> list(@ModelAttribute CrSearchCriteria criteria,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size) {
        return changeRequestService.search(criteria,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(CrDto::from);
    }

    @GetMapping("/{id}")
    public CrDto get(@PathVariable Long id) {
        return CrDto.from(changeRequestService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CR_MANAGER','SA_LEAD')")
    public CrDto create(@RequestBody CrForm form) {
        return CrDto.from(changeRequestService.create(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CR_MANAGER','SA_LEAD')")
    public CrDto update(@PathVariable Long id, @RequestBody CrForm form) {
        return CrDto.from(changeRequestService.update(id, form));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','CR_MANAGER','SA_LEAD')")
    public CrDto transition(@PathVariable Long id, @RequestParam CrStatus target) {
        crWorkflowService.transition(id, target);
        return CrDto.from(changeRequestService.getById(id));
    }
}
