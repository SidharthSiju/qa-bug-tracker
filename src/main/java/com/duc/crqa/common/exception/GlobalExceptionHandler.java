package com.duc.crqa.common.exception;

import com.duc.crqa.jira.exception.JiraException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ResourceNotFoundException ex, Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("error", "Not Found");
        model.addAttribute("message", ex.getMessage());
        return "error/404";
    }

    @ExceptionHandler(ConcurrencyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleConcurrency(ConcurrencyException ex, Model model) {
        model.addAttribute("status", 409);
        model.addAttribute("error", "Conflict");
        model.addAttribute("message", ex.getMessage());
        return "error/409";
    }

    @ExceptionHandler(WorkflowException.class)
    public String handleWorkflow(WorkflowException ex, RedirectAttributes redirectAttributes,
                                 HttpServletRequest request) {
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        String referer = request.getHeader("Referer");
        String target = referer != null && !referer.isBlank() ? referer : "/dashboard";
        return "redirect:" + target;
    }

    @ExceptionHandler(JiraException.class)
    public String handleJira(JiraException ex, RedirectAttributes redirectAttributes,
                             HttpServletRequest request) {
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        String referer = request.getHeader("Referer");
        String target = referer != null && !referer.isBlank() ? referer : "/change-requests";
        return "redirect:" + target;
    }
}
