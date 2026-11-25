package com.duc.crqa.testcase.service;

import com.duc.crqa.common.dto.BulkUploadResult;
import com.duc.crqa.common.enums.Priority;
import com.duc.crqa.common.util.CsvUtil;
import com.duc.crqa.cr.entity.ChangeRequest;
import com.duc.crqa.cr.repository.ChangeRequestRepository;
import com.duc.crqa.testcase.dto.TestCaseForm;
import com.duc.crqa.testcycle.entity.TestCycle;
import com.duc.crqa.testcycle.repository.TestCycleRepository;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TestCaseBulkUploadService {

    private final TestCaseService testCaseService;
    private final ChangeRequestRepository changeRequestRepository;
    private final TestCycleRepository testCycleRepository;

    public TestCaseBulkUploadService(TestCaseService testCaseService,
                                     ChangeRequestRepository changeRequestRepository,
                                     TestCycleRepository testCycleRepository) {
        this.testCaseService = testCaseService;
        this.changeRequestRepository = changeRequestRepository;
        this.testCycleRepository = testCycleRepository;
    }

    public BulkUploadResult importFile(MultipartFile file) throws IOException {
        List<CSVRecord> records = CsvUtil.parse(file);
        BulkUploadResult result = new BulkUploadResult();

        int rowNumber = 0;
        for (CSVRecord record : records) {
            rowNumber++;
            String cr = BulkUploadResult.part("CR", CsvUtil.value(record, "crNumber", "Epic Link", "epicLink"));
            String story = BulkUploadResult.part("story", CsvUtil.value(record, "storyNumber", "Story Link", "storyLink"));
            String title = BulkUploadResult.part("title", CsvUtil.value(record, "title", "Summary"));
            try {
                testCaseService.create(toForm(record));
                result.addSuccess();
            } catch (IllegalArgumentException e) {
                result.addError(rowNumber, BulkUploadResult.withReference(e.getMessage(), cr, story, title));
            } catch (Exception e) {
                result.addError(rowNumber,
                        BulkUploadResult.withReference("Failed to create test case: " + e.getMessage(), cr, story, title));
            }
        }
        return result;
    }

    private TestCaseForm toForm(CSVRecord record) {
        String crNumber = CsvUtil.value(record, "crNumber", "Epic Link", "epicLink");
        if (crNumber == null) {
            throw new IllegalArgumentException(
                    "crNumber is required (or supply the change request number in the Epic Link column)");
        }
        String title = CsvUtil.value(record, "title", "Summary");
        if (title == null) {
            throw new IllegalArgumentException("title (Summary) is required");
        }

        // Link to the CR by CR number ("Epic Link") and, when provided, story number ("Story Link").
        String storyNumber = CsvUtil.value(record, "storyNumber", "Story Link", "storyLink");
        ChangeRequest cr = (storyNumber != null
                ? changeRequestRepository.findFirstByCrNumberAndStoryNumberOrderByIdAsc(crNumber, storyNumber)
                : changeRequestRepository.findFirstByCrNumberOrderByIdAsc(crNumber))
                .orElseThrow(() -> new IllegalArgumentException(storyNumber != null
                        ? "Unknown change request for CR number " + crNumber + " and story number " + storyNumber
                        : "Unknown crNumber: " + crNumber));

        TestCaseForm form = new TestCaseForm();
        form.setCrId(cr.getId());
        form.setTitle(title);
        form.setDescription(CsvUtil.value(record, "description", "Description"));
        form.setPrecondition(CsvUtil.value(record, "precondition", "Precondition"));
        form.setTestSteps(CsvUtil.value(record, "testSteps", "Test Steps"));
        form.setExpectedResult(CsvUtil.value(record, "expectedResult", "Expected Result"));
        form.setPriority(parsePriority(CsvUtil.value(record, "priority", "Priority")));
        form.setLabels(CsvUtil.value(record, "labels", "Labels"));
        form.setIssueType(CsvUtil.value(record, "issueType", "Issue Type"));
        form.setIssueLink(CsvUtil.value(record, "issueLink", "Issue Link"));

        String cycleNumber = CsvUtil.value(record, "cycleNumber");
        if (cycleNumber != null) {
            int parsedCycleNumber;
            try {
                parsedCycleNumber = Integer.parseInt(cycleNumber.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid cycleNumber: " + cycleNumber);
            }
            TestCycle cycle = testCycleRepository.findByCrIdAndCycleNumber(cr.getId(), parsedCycleNumber)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown cycleNumber " + cycleNumber + " for CR " + crNumber));
            form.setCycleId(cycle.getId());
        }

        return form;
    }

    private Priority parsePriority(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Priority.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid priority: " + value);
        }
    }
}
