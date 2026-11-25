package com.duc.crqa.controller;

import com.duc.crqa.config.ConfigurationReloadService;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/config")
public class AdminConfigController {

    private final ConfigurationReloadService configurationReloadService;

    public AdminConfigController(ConfigurationReloadService configurationReloadService) {
        this.configurationReloadService = configurationReloadService;
    }

    @PostMapping("/reload")
    public String reload(RedirectAttributes redirectAttributes) {
        Map<String, String> result = configurationReloadService.reload();
        redirectAttributes.addFlashAttribute("success",
                "Configuration reloaded. Files loaded: " + result.get("filesLoaded")
                        + ", theme: " + result.get("app.theme")
                        + ", blocker SLA hours: " + result.get("app.sla.blocker-hours"));
        return "redirect:/dashboard";
    }
}
