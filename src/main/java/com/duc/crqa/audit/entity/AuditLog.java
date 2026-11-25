package com.duc.crqa.audit.entity;

import com.duc.crqa.common.entity.IdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "IT_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends IdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "IT_audit_log_seq_gen")
    @SequenceGenerator(name = "IT_audit_log_seq_gen", sequenceName = "IT_AUDIT_LOG_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(length = 100)
    private String username;

    @Column(length = 100)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "old_value", length = 4000)
    private String oldValue;

    @Column(name = "new_value", length = 4000)
    private String newValue;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
