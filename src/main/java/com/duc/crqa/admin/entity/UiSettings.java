package com.duc.crqa.admin.entity;

import com.duc.crqa.common.entity.IdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "IT_ui_settings")
@Getter
@Setter
@NoArgsConstructor
public class UiSettings extends IdEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(nullable = false, length = 20)
    private String theme;

    @Column(nullable = false, length = 30)
    private String font;

    @Column(name = "rows_per_page", nullable = false)
    private int rowsPerPage;

    @Column(name = "font_size", nullable = false)
    private int fontSize;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
