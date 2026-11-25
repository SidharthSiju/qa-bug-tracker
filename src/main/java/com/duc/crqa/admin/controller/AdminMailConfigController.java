package com.duc.crqa.admin.controller;

import com.duc.crqa.common.enums.MailTransport;
import com.duc.crqa.mail.dto.MailSettingsForm;
import com.duc.crqa.mail.service.MailConfigService;
import com.duc.crqa.notification.service.EmailNotifier;
import com.duc.crqa.security.CurrentUserService;
import com.duc.crqa.user.entity.User;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ADMIN-only screen for the SMTP relay used to email notifications, including a "send test email" action so the relay
 * can be verified from the application itself.
 */
@Controller
@RequestMapping("/admin/mail-config")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMailConfigController {

    private final MailConfigService mailConfigService;
    private final EmailNotifier emailNotifier;
    private final CurrentUserService currentUserService;

    public AdminMailConfigController(MailConfigService mailConfigService,
                                     EmailNotifier emailNotifier,
                                     CurrentUserService currentUserService) {
        this.mailConfigService = mailConfigService;
        this.emailNotifier = emailNotifier;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("config", mailConfigService.current());
        model.addAttribute("transports", MailTransport.values());
        model.addAttribute("transport", mailConfigService.transport());
        model.addAttribute("ready", mailConfigService.isReady());
        model.addAttribute("reason", mailConfigService.unavailableReason());
        model.addAttribute("timeoutLimit", MailConfigService.MAX_TIMEOUT_SECONDS);
        model.addAttribute("testRecipient", currentUserEmail());
        return "admin/mail-config";
    }

    @PostMapping
    public String save(@ModelAttribute MailSettingsForm form, RedirectAttributes redirectAttributes) {
        mailConfigService.update(form);
        redirectAttributes.addFlashAttribute("success", "Email settings saved");
        return "redirect:/admin/mail-config";
    }

    @PostMapping("/test")
    public String sendTest(@RequestParam(required = false) String to, RedirectAttributes redirectAttributes) {
        String recipient = to == null || to.isBlank() ? currentUserEmail() : to.trim();
        try {
            emailNotifier.sendNow(recipient, "CR Issue Tracker test email",
                    "This is a test email from the CR Issue Tracker.\n\n"
                            + "If you can read this, notification email is working.");
            redirectAttributes.addFlashAttribute("success", "Test email sent to " + recipient);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Could not send the test email: " + ex.getMessage());
        }
        return "redirect:/admin/mail-config";
    }

    private String currentUserEmail() {
        User user = currentUserService.currentUser();
        return user == null || user.getEmail() == null ? "" : user.getEmail();
    }
}
