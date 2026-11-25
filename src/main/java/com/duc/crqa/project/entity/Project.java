package com.duc.crqa.project.entity;

import com.duc.crqa.common.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "IT_project")
@Getter
@Setter
@NoArgsConstructor
public class Project extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "IT_project_seq_gen")
    @SequenceGenerator(name = "IT_project_seq_gen", sequenceName = "IT_PROJECT_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "project_code", nullable = false, unique = true, length = 50)
    private String projectCode;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(length = 4000)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    public String displayLabel() {
        return projectCode + " - " + projectName;
    }
}
