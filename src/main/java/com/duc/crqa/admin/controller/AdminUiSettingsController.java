package com.duc.crqa.admin.controller;

import com.duc.crqa.admin.service.UiSettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/ui-settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUiSettingsController {

    private final UiSettingsService uiSettingsService;

    public AdminUiSettingsController(UiSettingsService uiSettingsService) {
        this.uiSettingsService = uiSettingsService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("settings", uiSettingsService.current());
        model.addAttribute("themes", UiSettingsService.THEMES);
        model.addAttribute("fonts", UiSettingsService.FONTS);
        model.addAttribute("pageSizes", uiSettingsService.pageSizes());
        model.addAttribute("fontSizes", uiSettingsService.fontSizes());
        return "admin/ui-settings";
    }

    @PostMapping
    public String save(@RequestParam String theme,
                       @RequestParam String font,
                       @RequestParam int rowsPerPage,
                       @RequestParam int fontSize,
                       RedirectAttributes redirectAttributes) {
        uiSettingsService.update(theme, font, rowsPerPage, fontSize);
        redirectAttributes.addFlashAttribute("success", "UI settings updated for all users");
        return "redirect:/admin/ui-settings";
    }
}
