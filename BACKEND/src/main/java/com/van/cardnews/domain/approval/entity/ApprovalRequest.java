package com.van.cardnews.domain.approval.entity;

import com.van.cardnews.domain.content.entity.Content;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 콘텐츠 승인 요청 이력입니다.
 * 하나의 Content는 여러 번 승인 요청할 수 있습니다.
 */
@Entity
@Table(name = "approval_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    /**
     * 공통 Identity 서비스에서 전달받는 사용자 식별자.
     */
    @Column(name = "requester_id", length = 100)
    private String requesterId;

    /**
     * 공통 Identity 서비스에서 전달받는 승인자 식별자.
     */
    @Column(name = "approver_id", length = 100)
    private String approverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status;

    /**
     * 반려 사유.
     */
    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Builder
    private ApprovalRequest(
            Content content,
            String requesterId
    ) {
        this.content = content;
        this.requesterId = requesterId;
        this.status = ApprovalStatus.PENDING;
        this.requestedAt = LocalDateTime.now();
    }

    public static ApprovalRequest create(
            Content content,
            String requesterId
    ) {
        return ApprovalRequest.builder()
                .content(content)
                .requesterId(requesterId)
                .build();
    }

    /**
     * 승인 처리.
     */
    public void approve(String approverId) {
        validatePending();

        this.status = ApprovalStatus.APPROVED;
        this.approverId = approverId;
        this.processedAt = LocalDateTime.now();
        this.reason = null;
    }

    /**
     * 반려 처리.
     */
    public void reject(
            String approverId,
            String reason
    ) {
        validatePending();

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("반려 사유는 필수입니다.");
        }

        this.status = ApprovalStatus.REJECTED;
        this.approverId = approverId;
        this.reason = reason;
        this.processedAt = LocalDateTime.now();
    }

    private void validatePending() {
        if (this.status != ApprovalStatus.PENDING) {
            throw new IllegalStateException(
                    "대기 중인 승인 요청만 처리할 수 있습니다."
            );
        }
    }
}
