package com.duc.crqa.project.controller;

import com.duc.crqa.common.util.SortSupport;
import com.duc.crqa.project.dto.ProjectForm;
import com.duc.crqa.project.dto.ProjectSearchCriteria;
import com.duc.crqa.project.entity.Project;
import com.duc.crqa.project.service.ProjectService;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/admin/projects")
public class ProjectController {

    private static final Set<String> SORTABLE = Set.of("projectCode", "projectName", "description", "active");

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public String list(@ModelAttribute ProjectSearchCriteria criteria,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(defaultValue = "projectCode") String sort,
                       @RequestParam(defaultValue = "asc") String dir,
                       Model model) {
        model.addAttribute("page", projectService.search(criteria, PageRequest.of(page, size,
                SortSupport.resolve(sort, dir, SORTABLE, Sort.by(Sort.Direction.ASC, "projectCode")))));
        model.addAttribute("criteria", criteria);
        return "project/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("projectForm", new ProjectForm());
        return "project/create";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("projectForm") ProjectForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "project/create";
        }
        Project project = projectService.create(form);
        redirectAttributes.addFlashAttribute("success", "Project " + project.getProjectCode() + " created");
        return "redirect:/admin/projects";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Project project = projectService.getById(id);
        model.addAttribute("projectForm", toForm(project));
        model.addAttribute("project", project);
        return "project/edit";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("projectForm") ProjectForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("project", projectService.getById(id));
            return "project/edit";
        }
        projectService.update(id, form);
        redirectAttributes.addFlashAttribute("success", "Project updated");
        return "redirect:/admin/projects";
    }

    private ProjectForm toForm(Project project) {
        ProjectForm form = new ProjectForm();
        form.setProjectCode(project.getProjectCode());
        form.setProjectName(project.getProjectName());
        form.setDescription(project.getDescription());
        form.setActive(project.isActive());
        return form;
    }
}
