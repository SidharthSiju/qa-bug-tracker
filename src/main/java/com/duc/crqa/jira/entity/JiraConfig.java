package com.duc.crqa.jira.entity;

import com.duc.crqa.common.entity.IdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Singleton row (id = 1) holding the JIRA connection settings, editable by an ADMIN under Administration.
 */
@Entity
@Table(name = "IT_jira_config")
@Getter
@Setter
@NoArgsConstructor
public class JiraConfig extends IdEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * When true the JIRA search is run by the user's browser (which must be able to reach JIRA) and the result is
     * posted back to this application. It is the way to import from JIRA when the application server itself has no
     * network route to it.
     */
    @Column(name = "client_side", nullable = false)
    private boolean clientSide = false;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(length = 255)
    private String email;

    @Column(name = "api_token", length = 500)
    private String apiToken;

    /**
     * PEM certificate (or chain) of the authority that signs the JIRA server's certificate, used when this JVM does
     * not trust it — a private CA, or a reverse proxy such as nginx in front of JIRA. Blank uses the JVM's default
     * truststore.
     */
    @Column(name = "ca_certificate", length = 4000)
    private String caCertificate;

    @Column(name = "max_results", nullable = false)
    private int maxResults;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    @Column(length = 4000)
    private String jql;

    @Column(name = "field_list", length = 4000)
    private String fieldList;

    @Column(name = "status_list", length = 500)
    private String statusList;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
