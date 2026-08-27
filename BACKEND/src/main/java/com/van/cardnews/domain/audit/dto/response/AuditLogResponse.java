package com.van.cardnews.domain.audit.dto.response;

import com.van.cardnews.domain.audit.entity.AuditAction;
import com.van.cardnews.domain.audit.entity.AuditLog;
import com.van.cardnews.domain.audit.entity.AuditTargetType;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        String actorId,
        AuditAction action,
        AuditTargetType targetType,
        Long targetId,
        LocalDateTime occurredAt,
        String detail
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorId(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getOccurredAt(),
                log.getDetail()
        );
    }
}
