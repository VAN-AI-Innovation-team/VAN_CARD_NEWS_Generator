package com.van.cardnews.domain.audit.service;

import com.van.cardnews.domain.audit.dto.response.AuditLogResponse;
import com.van.cardnews.domain.audit.entity.AuditAction;
import com.van.cardnews.domain.audit.entity.AuditLog;
import com.van.cardnews.domain.audit.entity.AuditTargetType;
import com.van.cardnews.domain.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void record(
            String actorId,
            AuditAction action,
            Long contentId,
            String detail
    ) {
        auditLogRepository.save(
                AuditLog.create(
                        normalizeActorId(actorId),
                        action,
                        AuditTargetType.CONTENT,
                        contentId,
                        detail
                )
        );
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAll() {
        return auditLogRepository.findAllByOrderByOccurredAtDesc()
                .stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getByContentId(Long contentId) {
        return auditLogRepository.findByTargetIdOrderByOccurredAtDesc(contentId)
                .stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    private String normalizeActorId(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            return SYSTEM_ACTOR;
        }
        return actorId.trim();
    }
}
