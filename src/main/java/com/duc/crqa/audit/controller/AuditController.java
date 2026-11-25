package com.duc.crqa.audit.controller;

import com.duc.crqa.audit.dto.AuditSearchCriteria;
import com.duc.crqa.audit.entity.AuditLog;
import com.duc.crqa.audit.repository.AuditLogRepository;
import com.duc.crqa.common.util.SortSupport;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/audit")
public class AuditController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "username", "action", "entityType", "entityId");

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public String list(@ModelAttribute AuditSearchCriteria criteria,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "25") int size,
                       @RequestParam(defaultValue = "createdAt") String sort,
                       @RequestParam(defaultValue = "desc") String dir,
                       Model model) {
        model.addAttribute("page", auditLogRepository.findAll(buildSpecification(criteria),
                PageRequest.of(page, size,
                        SortSupport.resolve(sort, dir, SORTABLE, Sort.by(Sort.Direction.DESC, "createdAt")))));
        model.addAttribute("criteria", criteria);
        return "audit/list";
    }

    private Specification<AuditLog> buildSpecification(AuditSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.getSearch() != null && !criteria.getSearch().isBlank()) {
                String like = "%" + criteria.getSearch().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("action")), like),
                        cb.like(cb.lower(root.get("entityType")), like)));
            }
            if (criteria.getAction() != null && !criteria.getAction().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("action")),
                        "%" + criteria.getAction().toLowerCase() + "%"));
            }
            if (criteria.getEntityType() != null && !criteria.getEntityType().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("entityType")),
                        criteria.getEntityType().toLowerCase()));
            }
            if (criteria.getFromDate() != null && !criteria.getFromDate().isBlank()) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        LocalDate.parse(criteria.getFromDate()).atStartOfDay()));
            }
            if (criteria.getToDate() != null && !criteria.getToDate().isBlank()) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"),
                        LocalDate.parse(criteria.getToDate()).atTime(23, 59, 59)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
