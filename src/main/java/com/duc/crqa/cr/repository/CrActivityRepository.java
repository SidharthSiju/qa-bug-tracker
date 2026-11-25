package com.duc.crqa.cr.repository;

import com.duc.crqa.cr.entity.CrActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CrActivityRepository extends JpaRepository<CrActivity, Long>, JpaSpecificationExecutor<CrActivity> {

    List<CrActivity> findByCrIdOrderByPerformedAtDesc(Long crId);
}
