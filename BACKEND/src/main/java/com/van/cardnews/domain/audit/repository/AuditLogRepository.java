package com.van.cardnews.domain.audit.repository;

import com.van.cardnews.domain.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByOccurredAtDesc();

    List<AuditLog> findByTargetIdOrderByOccurredAtDesc(Long targetId);
}
