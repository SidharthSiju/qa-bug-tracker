package com.duc.crqa.audit.repository;

import com.duc.crqa.audit.entity.AuditLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    @Query("select a.username, a.action, count(a) from AuditLog a "
            + "where a.createdAt >= :from and a.createdAt <= :to "
            + "group by a.username, a.action")
    List<Object[]> countByUserAndActionBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
