package com.duc.crqa.project.service;

import com.duc.crqa.audit.service.AuditService;
import com.duc.crqa.common.exception.ResourceNotFoundException;
import com.duc.crqa.project.dto.ProjectForm;
import com.duc.crqa.project.dto.ProjectSearchCriteria;
import com.duc.crqa.project.entity.Project;
import com.duc.crqa.project.repository.ProjectRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    public static final String DEFAULT_PROJECT_CODE = "DEFAULT";

    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    public ProjectService(ProjectRepository projectRepository, AuditService auditService) {
        this.projectRepository = projectRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Project> listAll() {
        return projectRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Project> listActive() {
        return projectRepository.findByActiveTrueOrderByProjectCodeAsc();
    }

    @Transactional(readOnly = true)
    public Page<Project> search(ProjectSearchCriteria criteria, Pageable pageable) {
        return projectRepository.findAll(buildSpecification(criteria), pageable);
    }

    private Specification<Project> buildSpecification(ProjectSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.getSearch() != null && !criteria.getSearch().isBlank()) {
                String like = "%" + criteria.getSearch().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("projectCode")), like),
                        cb.like(cb.lower(root.get("projectName")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            if (criteria.getActive() != null) {
                predicates.add(cb.equal(root.get("active"), criteria.getActive()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public Project getById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }

    @Transactional
    public Project getOrCreateDefaultProject() {
        return projectRepository.findByProjectCode(DEFAULT_PROJECT_CODE)
                .orElseGet(() -> {
                    Project project = new Project();
                    project.setProjectCode(DEFAULT_PROJECT_CODE);
                    project.setProjectName("Default");
                    project.setDescription("Default project for change requests without an assigned project");
                    project.setActive(true);
                    Project saved = projectRepository.save(project);
                    auditService.record("PROJECT_CREATED", "PROJECT", saved.getId(), null, saved.getProjectCode());
                    return saved;
                });
    }

    @Transactional
    public Project create(ProjectForm form) {
        Project project = new Project();
        applyForm(project, form);
        Project saved = projectRepository.save(project);
        auditService.record("PROJECT_CREATED", "PROJECT", saved.getId(), null, saved.getProjectCode());
        return saved;
    }

    @Transactional
    public Project update(Long id, ProjectForm form) {
        Project project = getById(id);
        String oldValue = project.getProjectCode();
        applyForm(project, form);
        Project saved = projectRepository.save(project);
        auditService.record("PROJECT_UPDATED", "PROJECT", saved.getId(), oldValue, saved.getProjectCode());
        return saved;
    }

    private void applyForm(Project project, ProjectForm form) {
        project.setProjectCode(form.getProjectCode());
        project.setProjectName(form.getProjectName());
        project.setDescription(form.getDescription());
        project.setActive(form.isActive());
    }
}
