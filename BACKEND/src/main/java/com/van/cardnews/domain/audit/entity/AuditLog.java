package com.van.cardnews.domain.audit.entity;

import com.van.cardnews.global.time.KoreaTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false, length = 100)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private AuditTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Builder
    private AuditLog(
            String actorId,
            AuditAction action,
            AuditTargetType targetType,
            Long targetId,
            String detail
    ) {
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.occurredAt = KoreaTime.now();
        this.detail = detail;
    }

    public static AuditLog create(
            String actorId,
            AuditAction action,
            AuditTargetType targetType,
            Long targetId,
            String detail
    ) {
        return AuditLog.builder()
                .actorId(actorId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .detail(detail)
                .build();
    }
}
