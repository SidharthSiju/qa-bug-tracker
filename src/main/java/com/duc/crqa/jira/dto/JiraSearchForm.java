package com.duc.crqa.jira.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * The two filters offered by the "Import from JIRA" search form: everything created since a date, in a given status.
 * They fill the {@code {{createdFrom}}} and {@code {{status}}} placeholders of the configured JQL.
 */
@Getter
@Setter
@NoArgsConstructor
public class JiraSearchForm {

//    @NotNull(message = "From date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate = LocalDate.now().minusDays(30);

//    @NotBlank(message = "Status is required")
    private String status;

    private String storyNumber;
    
    private int page = 0;

    private int size = 10;
}
