package com.duc.crqa.analytics.repository;

import com.duc.crqa.analytics.entity.UserActivity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {

    @Query("select a.user.id, a.feature, sum(a.durationSeconds), count(a) from UserActivity a "
            + "where a.startedAt >= :from and a.startedAt <= :to "
            + "group by a.user.id, a.feature")
    List<Object[]> sumByUserAndFeatureBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
