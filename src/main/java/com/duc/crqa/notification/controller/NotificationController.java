package com.duc.crqa.notification.controller;

import com.duc.crqa.common.exception.ResourceNotFoundException;
import com.duc.crqa.notification.entity.Notification;
import com.duc.crqa.notification.repository.NotificationRepository;
import com.duc.crqa.security.CurrentUserService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationRepository notificationRepository,
                                  CurrentUserService currentUserService) {
        this.notificationRepository = notificationRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public String list(Model model) {
        Long userId = currentUserService.currentUserId();
        if (userId != null) {
            model.addAttribute("notifications",
                    notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId));
        }
        return "notification/list";
    }

    @PostMapping("/{id}/read")
    public String markRead(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        notification.setReadFlag(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
        redirectAttributes.addFlashAttribute("success", "Notification marked as read");
        return "redirect:/notifications";
    }
}
