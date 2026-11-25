package com.duc.crqa.cr.service;

import com.duc.crqa.common.dto.BulkUploadResult;
import com.duc.crqa.common.enums.Complexity;
import com.duc.crqa.common.enums.CrPriority;
import com.duc.crqa.common.util.CsvUtil;
import com.duc.crqa.common.util.DateParser;
import com.duc.crqa.cr.dto.CrForm;
import com.duc.crqa.project.entity.Project;
import com.duc.crqa.project.repository.ProjectRepository;
import com.duc.crqa.user.repository.UserRepository;
import com.duc.crqa.user.service.UserLookup;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CrBulkUploadService {

    private final ChangeRequestService changeRequestService;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public CrBulkUploadService(ChangeRequestService changeRequestService,
                               ProjectRepository projectRepository,
                               UserRepository userRepository) {
        this.changeRequestService = changeRequestService;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    public BulkUploadResult importFile(MultipartFile file) throws IOException {
        List<CSVRecord> records = CsvUtil.parse(file);
        BulkUploadResult result = new BulkUploadResult();

        UserLookup users = new UserLookup(userRepository.findAll());

        int rowNumber = 0;
        for (CSVRecord record : records) {
            rowNumber++;
            String epic = BulkUploadResult.part("Epic/CR", crNumber(record));
            String name = BulkUploadResult.part("name", CsvUtil.value(record, "crName"));
            try {
                changeRequestService.create(toForm(record, users));
                result.addSuccess();
            } catch (IllegalArgumentException e) {
                result.addError(rowNumber, BulkUploadResult.withReference(e.getMessage(), epic, name));
            } catch (Exception e) {
                result.addError(rowNumber,
                        BulkUploadResult.withReference("Failed to create change request: " + e.getMessage(), epic, name));
            }
        }
        return result;
    }

    /** The row's epic/CR number, or {@code null} when the column is blank (the number is then auto-generated). */
    private static String crNumber(CSVRecord record) {
        return CsvUtil.value(record, "crNumber", "epicNumber", "Epic Number", "CR Number");
    }

    private CrForm toForm(CSVRecord record, UserLookup users) {
        String crName = CsvUtil.value(record, "crName");
        if (crName == null) {
            throw new IllegalArgumentException("crName is required");
        }

        CrForm form = new CrForm();
        form.setCrNumber(crNumber(record));
        form.setCrName(crName);
        form.setDescription(CsvUtil.value(record, "description"));
        form.setEnvironment(CsvUtil.value(record, "environment"));
        form.setSprintName(CsvUtil.value(record, "sprintName", "sprint", "Sprint Name"));
        form.setStoryNumber(CsvUtil.value(record, "storyNumber", "story", "Story Number"));
        form.setPriority(parseEnum(CsvUtil.value(record, "priority"), CrPriority.MEDIUM,
                CrPriority.class, "priority"));
        form.setComplexity(parseEnum(CsvUtil.value(record, "complexity"), Complexity.MEDIUM,
                Complexity.class, "complexity"));

        String projectCode = CsvUtil.value(record, "projectCode");
        if (projectCode != null) {
            Project project = projectRepository.findByProjectCode(projectCode)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown projectCode: " + projectCode));
            form.setProjectId(project.getId());
        }

        form.setDevelopmentOwnerId(resolveUserId(users,
                CsvUtil.value(record, "developmentOwnerUsername"), "developmentOwnerUsername"));
        form.setQaLeadId(resolveUserId(users, CsvUtil.value(record, "qaLeadUsername"), "qaLeadUsername"));
        form.setSaLeadId(resolveUserId(users, CsvUtil.value(record, "saLeadUsername"), "saLeadUsername"));

        form.setPlannedStartDate(parseDate(CsvUtil.value(record, "plannedStartDate"), "plannedStartDate"));
        form.setPlannedEndDate(parseDate(CsvUtil.value(record, "plannedEndDate"), "plannedEndDate"));
        form.setActualStartDate(parseDate(CsvUtil.value(record, "actualStartDate", "Actual Start Date"), "actualStartDate"));
        form.setActualEndDate(parseDate(CsvUtil.value(record, "actualEndDate", "Actual End Date"), "actualEndDate"));
        form.setDacSubmitDate(parseDate(CsvUtil.value(record, "dacSubmitDate", "DAC Submit Date"), "dacSubmitDate"));
        form.setHldSubmitDate(parseDate(CsvUtil.value(record, "hldSubmitDate", "HLD Submit Date"), "hldSubmitDate"));
        return form;
    }

    private <E extends Enum<E>> E parseEnum(String value, E defaultValue, Class<E> type, String field) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + field + ": " + value);
        }
    }

    /** Resolves an owner column given as a username or a display name; {@code null} when the column is blank. */
    private Long resolveUserId(UserLookup users, String value, String field) {
        if (value == null) {
            return null;
        }
        Long id = users.resolve(value);
        if (id == null) {
            throw new IllegalArgumentException("Unknown " + field + ": " + value + " (username or display name)");
        }
        return id;
    }

    private LocalDate parseDate(String value, String field) {
        if (value == null) {
            return null;
        }
        LocalDate date = DateParser.parse(value);
        if (date == null) {
            throw new IllegalArgumentException(
                    "Invalid " + field + ": " + value + " (expected a date such as yyyy-MM-dd)");
        }
        return date;
    }
}
