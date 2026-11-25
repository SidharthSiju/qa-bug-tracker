package com.duc.crqa.mail.repository;

import com.duc.crqa.mail.entity.MailConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailConfigRepository extends JpaRepository<MailConfig, Long> {
}
