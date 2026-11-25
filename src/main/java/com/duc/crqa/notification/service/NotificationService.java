package com.duc.crqa.notification.service;

import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.common.enums.SlaStatus;
import com.duc.crqa.common.enums.TestCaseReviewStage;
import com.duc.crqa.cr.entity.ChangeRequest;

public interface NotificationService {

    void notifyBugCreated(Bug bug);

    void notifyBugAssigned(Bug bug);

    void notifyBugFixed(Bug bug);

    void notifyBugReadyForRetest(Bug bug);

    void notifyBugReopened(Bug bug);

    void notifySlaBreach(Bug bug, SlaStatus slaStatus);

    void notifyCrReadyForQa(ChangeRequest cr);

    void notifyCrCompleted(ChangeRequest cr);

    void notifyCrUpdatedFromJira(ChangeRequest cr);

    void notifyTestCaseReview(ChangeRequest cr, TestCaseReviewStage stage);
}
