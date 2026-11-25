package com.duc.crqa.jira.repository;

import com.duc.crqa.jira.entity.JiraConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JiraConfigRepository extends JpaRepository<JiraConfig, Long> {
}
