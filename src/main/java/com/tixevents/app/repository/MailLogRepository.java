package com.tixevents.app.repository;

import com.tixevents.app.entity.MailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MailLogRepository extends JpaRepository<MailLog, Long> {
}
