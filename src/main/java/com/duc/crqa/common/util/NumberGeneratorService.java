package com.duc.crqa.common.util;

import com.duc.crqa.bug.repository.BugRepository;
import com.duc.crqa.cr.repository.ChangeRequestRepository;
import com.duc.crqa.testcase.repository.TestCaseRepository;
import java.time.Year;
import org.springframework.stereotype.Service;

@Service
public class NumberGeneratorService {

    private final BugRepository bugRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final TestCaseRepository testCaseRepository;

    public NumberGeneratorService(BugRepository bugRepository,
                                  ChangeRequestRepository changeRequestRepository,
                                  TestCaseRepository testCaseRepository) {
        this.bugRepository = bugRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.testCaseRepository = testCaseRepository;
    }

    public String nextBugNumber() {
        int year = Year.now().getValue();
        String prefix = "BUG-" + year + "-";
        long count = bugRepository.countByBugNumberStartingWith(prefix);
        return String.format("BUG-%d-%06d", year, count + 1);
    }

    public String nextCrNumber() {
        int year = Year.now().getValue();
        String prefix = "CR-" + year + "-";
        long count = changeRequestRepository.countByCrNumberStartingWith(prefix);
        return String.format("CR-%d-%06d", year, count + 1);
    }

    public String nextTestCaseNumber() {
        int year = Year.now().getValue();
        String prefix = "TC-" + year + "-";
        long count = testCaseRepository.countByTestCaseNumberStartingWith(prefix);
        return String.format("TC-%d-%06d", year, count + 1);
    }
}
