package com.duc.crqa.common.archive.service;

import com.duc.crqa.security.CurrentUserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Copies deleted records into the {@code IT_*_archive} tables before removing them from the live tables.
 *
 * <p>Archive tables are created (in each dialect) with the source columns in order plus {@code archived_at} and
 * {@code archived_by}, so rows are copied with {@code INSERT INTO <archive> SELECT t.*, :at, :by FROM <source> t}.
 */
@Service
public class ArchiveService {

    private static final String CHANGE_REQUEST = "IT_change_request";
    private static final String CR_ACTIVITY = "IT_cr_activity";
    private static final String TEST_CYCLE = "IT_test_cycle";
    private static final String TEST_CASE = "IT_test_case";
    private static final String TEST_CASE_ACTIVITY = "IT_test_case_activity";
    private static final String BUG = "IT_bug";
    private static final String BUG_COMMENT = "IT_bug_comment";
    private static final String BUG_ATTACHMENT = "IT_bug_attachment";
    private static final String BUG_HISTORY = "IT_bug_history";

    private final CurrentUserService currentUserService;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${spring.jpa.properties.hibernate.default_schema:}")
    private String schema;

    public ArchiveService(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Transactional
    public int archiveAndDeleteTestCases(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        LocalDateTime archivedAt = LocalDateTime.now();
        Long archivedBy = currentUserService.currentUserId();
        archiveRows(TEST_CASE, "id", ids, archivedAt, archivedBy);
        archiveRows(TEST_CASE_ACTIVITY, "test_case_id", ids, archivedAt, archivedBy);
        // Unlink any bug that points at a deleted test case (bug.test_case_id is nullable).
        entityManager.createNativeQuery("update " + table(BUG) + " set test_case_id = null where test_case_id in (:ids)")
                .setParameter("ids", ids)
                .executeUpdate();
        // The activity rows reference the test case, so remove them first.
        deleteRows(TEST_CASE_ACTIVITY, "test_case_id", ids);
        return deleteRows(TEST_CASE, "id", ids);
    }

    @Transactional
    public int archiveAndDeleteTestCycles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        LocalDateTime archivedAt = LocalDateTime.now();
        Long archivedBy = currentUserService.currentUserId();
        archiveRows(TEST_CYCLE, "id", ids, archivedAt, archivedBy);
        // test_case.cycle_id is nullable — unlink test cases before removing the cycle.
        entityManager.createNativeQuery("update " + table(TEST_CASE) + " set cycle_id = null where cycle_id in (:ids)")
                .setParameter("ids", ids)
                .executeUpdate();
        return deleteRows(TEST_CYCLE, "id", ids);
    }

    @Transactional
    public int archiveAndDeleteBugs(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        LocalDateTime archivedAt = LocalDateTime.now();
        Long archivedBy = currentUserService.currentUserId();
        archiveRows(BUG_COMMENT, "bug_id", ids, archivedAt, archivedBy);
        archiveRows(BUG_HISTORY, "bug_id", ids, archivedAt, archivedBy);
        archiveRows(BUG_ATTACHMENT, "bug_id", ids, archivedAt, archivedBy);
        archiveRows(BUG, "id", ids, archivedAt, archivedBy);
        deleteRows(BUG_COMMENT, "bug_id", ids);
        deleteRows(BUG_HISTORY, "bug_id", ids);
        deleteRows(BUG_ATTACHMENT, "bug_id", ids);
        return deleteRows(BUG, "id", ids);
    }

    @Transactional
    public void archiveAndDeleteChangeRequest(Long crId) {
        if (crId == null) {
            return;
        }
        LocalDateTime archivedAt = LocalDateTime.now();
        Long archivedBy = currentUserService.currentUserId();

        List<Long> bugIds = ids("select id from " + table(BUG) + " where cr_id = :ownerId", "ownerId", crId);
        if (!bugIds.isEmpty()) {
            archiveRows(BUG_COMMENT, "bug_id", bugIds, archivedAt, archivedBy);
            archiveRows(BUG_HISTORY, "bug_id", bugIds, archivedAt, archivedBy);
            archiveRows(BUG_ATTACHMENT, "bug_id", bugIds, archivedAt, archivedBy);
        }
        List<Long> testCaseIds =
                ids("select id from " + table(TEST_CASE) + " where cr_id = :ownerId", "ownerId", crId);
        if (!testCaseIds.isEmpty()) {
            archiveRows(TEST_CASE_ACTIVITY, "test_case_id", testCaseIds, archivedAt, archivedBy);
        }
        archiveByOwner(BUG, crId, archivedAt, archivedBy);
        archiveByOwner(TEST_CASE, crId, archivedAt, archivedBy);
        archiveByOwner(TEST_CYCLE, crId, archivedAt, archivedBy);
        archiveByOwner(CR_ACTIVITY, crId, archivedAt, archivedBy);
        archiveRows(CHANGE_REQUEST, "id", List.of(crId), archivedAt, archivedBy);

        if (!bugIds.isEmpty()) {
            deleteRows(BUG_COMMENT, "bug_id", bugIds);
            deleteRows(BUG_HISTORY, "bug_id", bugIds);
            deleteRows(BUG_ATTACHMENT, "bug_id", bugIds);
        }
        if (!testCaseIds.isEmpty()) {
            deleteRows(TEST_CASE_ACTIVITY, "test_case_id", testCaseIds);
        }
        deleteByOwner(BUG, crId);
        deleteByOwner(TEST_CASE, crId);
        deleteByOwner(TEST_CYCLE, crId);
        deleteByOwner(CR_ACTIVITY, crId);
        deleteRows(CHANGE_REQUEST, "id", List.of(crId));
    }

    private void archiveRows(String sourceTable, String column, List<Long> ids, LocalDateTime archivedAt, Long archivedBy) {
        String sql = "insert into " + table(sourceTable + "_archive")
                + " select t.*, :archivedAt, :archivedBy from " + table(sourceTable) + " t where t." + column + " in (:ids)";
        entityManager.createNativeQuery(sql)
                .setParameter("archivedAt", archivedAt)
                .setParameter("archivedBy", archivedBy)
                .setParameter("ids", ids)
                .executeUpdate();
    }

    private void archiveByOwner(String sourceTable, Long ownerId, LocalDateTime archivedAt, Long archivedBy) {
        String sql = "insert into " + table(sourceTable + "_archive")
                + " select t.*, :archivedAt, :archivedBy from " + table(sourceTable) + " t where t.cr_id = :ownerId";
        entityManager.createNativeQuery(sql)
                .setParameter("archivedAt", archivedAt)
                .setParameter("archivedBy", archivedBy)
                .setParameter("ownerId", ownerId)
                .executeUpdate();
    }

    private int deleteRows(String sourceTable, String column, List<Long> ids) {
        return entityManager.createNativeQuery("delete from " + table(sourceTable) + " where " + column + " in (:ids)")
                .setParameter("ids", ids)
                .executeUpdate();
    }

    private void deleteByOwner(String sourceTable, Long ownerId) {
        entityManager.createNativeQuery("delete from " + table(sourceTable) + " where cr_id = :ownerId")
                .setParameter("ownerId", ownerId)
                .executeUpdate();
    }

    private List<Long> ids(String sql, String parameter, Long value) {
        List<?> rows = entityManager.createNativeQuery(sql).setParameter(parameter, value).getResultList();
        List<Long> result = new ArrayList<>(rows.size());
        for (Object row : rows) {
            result.add(((Number) row).longValue());
        }
        return result;
    }

    private String table(String name) {
        return schema == null || schema.isBlank() ? name : schema + "." + name;
    }
}
