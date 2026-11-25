package com.duc.crqa.cr.repository;

import com.duc.crqa.common.enums.CrStatus;
import com.duc.crqa.cr.entity.ChangeRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, Long>,
        JpaSpecificationExecutor<ChangeRequest> {

    Optional<ChangeRequest> findFirstByCrNumberOrderByIdAsc(String crNumber);

    Optional<ChangeRequest> findFirstByCrNumberAndCrName(String crNumber, String crName);

    Optional<ChangeRequest> findFirstByCrNumberAndStoryNumberOrderByIdAsc(String crNumber, String storyNumber);

    boolean existsByCrNumberAndCrName(String crNumber, String crName);

    boolean existsByCrNumberAndCrNameAndIdNot(String crNumber, String crName, Long id);

    long countByStatus(CrStatus status);

    long countByStatusIn(List<CrStatus> statuses);

    long countByCrNumberStartingWith(String prefix);

    List<ChangeRequest> findAllByOrderByCreatedAtDesc();

    Page<ChangeRequest> findByStatusInOrderByCreatedAtDesc(List<CrStatus> statuses, Pageable pageable);

    @Query("select c.status, count(c) from ChangeRequest c group by c.status")
    List<Object[]> countGroupByStatus();
}
