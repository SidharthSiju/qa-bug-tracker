package com.duc.crqa.user.controller;

import com.duc.crqa.common.enums.Role;
import com.duc.crqa.common.util.SampleCsv;
import com.duc.crqa.common.util.SortSupport;
import com.duc.crqa.user.dto.UserForm;
import com.duc.crqa.user.dto.UserSearchCriteria;
import com.duc.crqa.user.entity.User;
import com.duc.crqa.user.service.UserBulkUploadService;
import com.duc.crqa.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/admin/users")
public class UserController {

    private static final Set<String> SORTABLE = Set.of("username", "displayName", "email", "active");

    private final UserService userService;
    private final UserBulkUploadService userBulkUploadService;

    public UserController(UserService userService, UserBulkUploadService userBulkUploadService) {
        this.userService = userService;
        this.userBulkUploadService = userBulkUploadService;
    }

    @GetMapping
    public String list(@ModelAttribute UserSearchCriteria criteria,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(defaultValue = "username") String sort,
                       @RequestParam(defaultValue = "asc") String dir,
                       Model model) {
        model.addAttribute("page", userService.search(criteria, PageRequest.of(page, size,
                SortSupport.resolve(sort, dir, SORTABLE, Sort.by(Sort.Direction.ASC, "username")))));
        model.addAttribute("criteria", criteria);
        model.addAttribute("roles", Role.values());
        return "user/list";
    }

    @GetMapping("/upload")
    public String uploadForm() {
        return "user/upload";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file, Model model) {
        try {
            model.addAttribute("result", userBulkUploadService.importFile(file));
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "user/upload";
    }

    @GetMapping("/upload/template")
    public void uploadTemplate(HttpServletResponse response) throws IOException {
        SampleCsv.write("samples/user_upload_template.csv", "user_upload_template.csv", response);
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("userForm", new UserForm());
        model.addAttribute("roles", Role.values());
        return "user/create";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("userForm") UserForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (form.getPassword() == null || form.getPassword().isBlank()) {
            bindingResult.rejectValue("password", "required", "Password is required");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("roles", Role.values());
            return "user/create";
        }
        User user = userService.create(form);
        redirectAttributes.addFlashAttribute("success", "User " + user.getUsername() + " created");
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        User user = userService.getById(id);
        model.addAttribute("userForm", toForm(user));
        model.addAttribute("user", user);
        model.addAttribute("roles", Role.values());
        return "user/edit";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("userForm") UserForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("user", userService.getById(id));
            model.addAttribute("roles", Role.values());
            return "user/edit";
        }
        userService.update(id, form);
        redirectAttributes.addFlashAttribute("success", "User updated");
        return "redirect:/admin/users";
    }

    private UserForm toForm(User user) {
        UserForm form = new UserForm();
        form.setUsername(user.getUsername());
        form.setDisplayName(user.getDisplayName());
        form.setEmail(user.getEmail());
        form.setActive(user.isActive());
        form.setEmailNotifications(user.isEmailNotifications());
        form.setRoles(user.getRoles());
        return form;
    }
}
