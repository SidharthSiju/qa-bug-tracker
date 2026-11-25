package com.duc.crqa.project.repository;

import com.duc.crqa.project.entity.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    List<Project> findByActiveTrueOrderByProjectCodeAsc();

    Optional<Project> findByProjectCode(String projectCode);
}
