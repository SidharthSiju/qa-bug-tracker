package com.duc.crqa.audit.service;

import com.duc.crqa.audit.entity.AuditLog;
import com.duc.crqa.audit.repository.AuditLogRepository;
import com.duc.crqa.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private static final int MAX_LENGTH = 4000;

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;

    public AuditService(AuditLogRepository auditLogRepository, CurrentUserService currentUserService) {
        this.auditLogRepository = auditLogRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String entityType, Long entityId, String oldValue, String newValue) {
        record(currentUserService.currentUsername(), action, entityType, entityId, oldValue, newValue);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String username, String action, String entityType, Long entityId,
                       String oldValue, String newValue) {
        AuditLog log = new AuditLog();
        log.setUsername(username);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOldValue(truncate(oldValue));
        log.setNewValue(truncate(newValue));

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            log.setIpAddress(request.getRemoteAddr());
            log.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
        }

        auditLogRepository.save(log);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
