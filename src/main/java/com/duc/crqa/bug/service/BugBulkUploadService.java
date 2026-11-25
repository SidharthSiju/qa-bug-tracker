package com.duc.crqa.bug.service;

import com.duc.crqa.bug.dto.BugForm;
import com.duc.crqa.common.dto.BulkUploadResult;
import com.duc.crqa.common.enums.BugStatus;
import com.duc.crqa.common.enums.DefectCategory;
import com.duc.crqa.common.enums.Priority;
import com.duc.crqa.common.enums.Severity;
import com.duc.crqa.common.enums.TestPhase;
import com.duc.crqa.common.util.CsvUtil;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.repository.ChangeRequestRepository;
import com.duc.crqa.user.repository.UserRepository;
import com.duc.crqa.user.service.UserLookup;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BugBulkUploadService {

    private final BugService bugService;
    private final ChangeRequestRepository changeRequestRepository;
    private final UserRepository userRepository;

    public BugBulkUploadService(BugService bugService,
                                ChangeRequestRepository changeRequestRepository,
                                UserRepository userRepository) {
        this.bugService = bugService;
        this.changeRequestRepository = changeRequestRepository;
        this.userRepository = userRepository;
    }

    public BulkUploadResult importFile(MultipartFile file) throws IOException {
        List<CSVRecord> records = CsvUtil.parse(file);
        BulkUploadResult result = new BulkUploadResult();
        UserLookup users = new UserLookup(userRepository.findAll());

        int rowNumber = 0;
        for (CSVRecord record : records) {
            rowNumber++;
            String cr = BulkUploadResult.part("CR", value(record, "CR Number", "CR ID", "crNumber", "crNo", "crId"));
            String summary = BulkUploadResult.part("summary", value(record, "Summary", "title"));
            try {
                bugService.create(toForm(record, users), List.of(), false);
                result.addSuccess();
            } catch (IllegalArgumentException e) {
                result.addError(rowNumber, BulkUploadResult.withReference(e.getMessage(), cr, summary));
            } catch (Exception e) {
                result.addError(rowNumber,
                        BulkUploadResult.withReference("Failed to create bug: " + e.getMessage(), cr, summary));
            }
        }
        return result;
    }

    private BugForm toForm(CSVRecord record, UserLookup users) {
        String crNumber = value(record, "CR Number", "CR ID", "crNumber", "crNo", "crId");
        if (crNumber == null) {
            throw new IllegalArgumentException("CR Number is required");
        }
        ChangeRequest cr = changeRequestRepository.findFirstByCrNumberOrderByIdAsc(crNumber.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown CR Number: " + crNumber + " (create the change request first)"));

        String title = value(record, "Summary", "title");
        if (title == null) {
            throw new IllegalArgumentException("Summary is required");
        }

        String severityRaw = value(record, "Severity", "severity");
        String priorityRaw = value(record, "Priority", "priority");
        Severity severity = parseSeverity(severityRaw);
        Priority priority = parsePriority(priorityRaw);
        if (severity == null && priority == null) {
            priority = parsePriority(severityRaw);
        }
        if (severity == null) {
            severity = Severity.MAJOR;
        }
        if (priority == null) {
            priority = Priority.P2;
        }

        BugForm form = new BugForm();
        form.setCrId(cr.getId());
        form.setTitle(title);
        String description = value(record, "Description", "description");
        form.setDescription(description == null ? title : description);
        form.setStepsToReproduce(value(record, "Steps to Reproduce", "stepsToReproduce"));
        form.setExpectedResult(value(record, "Expected Result", "expectedResult"));
        form.setActualResult(value(record, "Actual Result", "actualResult"));
        form.setSeverity(severity);
        form.setPriority(priority);
        form.setStatus(parseStatus(value(record, "Status", "status")));
        form.setSprint(value(record, "Sprint", "sprint"));
        form.setEnvironment(value(record, "Environment", "environment"));
        form.setApplication(value(record, "Application", "application"));
        form.setModule(value(record, "Module", "module"));
        form.setExternalDefectId(value(record, "Defect ID", "externalDefectId", "defectId"));
        form.setDefectCategory(DefectCategory.fromLabel(
                value(record, "Defect Category", "defectCategory")));
        form.setTestPhaseFound(TestPhase.fromValue(
                value(record, "Test Phase Found", "Test Phase", "testPhaseFound", "testPhase")));
        form.setReportedById(users.resolve(value(record, "Logged By", "loggedBy", "reportedBy",
                "reportedByUsername")));
        form.setAssignedToId(users.resolve(value(record, "Assigned To", "assignedTo",
                "assignedToUsername")));
        return form;
    }

    private String value(CSVRecord record, String... headers) {
        for (String header : headers) {
            String value = blankToNull(CsvUtil.value(record, header));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Severity parseSeverity(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "BLOCKER" -> Severity.BLOCKER;
            case "CRITICAL" -> Severity.CRITICAL;
            case "MAJOR" -> Severity.MAJOR;
            case "MINOR" -> Severity.MINOR;
            case "TRIVIAL" -> Severity.TRIVIAL;
            default -> null;
        };
    }

    private Priority parsePriority(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "P1", "1" -> Priority.P1;
            case "P2", "2" -> Priority.P2;
            case "P3", "3" -> Priority.P3;
            case "P4", "4" -> Priority.P4;
            default -> null;
        };
    }

    private BugStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_')) {
            case "OPEN", "NEW" -> BugStatus.OPEN;
            case "ASSIGNED" -> BugStatus.ASSIGNED;
            case "IN_ANALYSIS", "ANALYSIS" -> BugStatus.IN_ANALYSIS;
            case "IN_DEVELOPMENT", "IN_PROGRESS", "DEVELOPMENT", "WIP" -> BugStatus.IN_DEVELOPMENT;
            case "FIXED" -> BugStatus.FIXED;
            case "READY_FOR_RETEST" -> BugStatus.READY_FOR_RETEST;
            case "RETESTING", "RETEST" -> BugStatus.RETESTING;
            case "CLOSED", "VERIFIED", "DONE" -> BugStatus.CLOSED;
            case "REOPENED", "RE_OPENED" -> BugStatus.REOPENED;
            case "REJECTED" -> BugStatus.REJECTED;
            default -> throw new IllegalArgumentException("Invalid status: " + value);
        };
    }
}
