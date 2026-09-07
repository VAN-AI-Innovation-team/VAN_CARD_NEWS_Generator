package com.van.cardnews.domain.approval.dto.response;

import com.van.cardnews.domain.approval.entity.ApprovalRequest;
import java.time.LocalDateTime;

public record ApprovalRequestResponse(
        Long approvalRequestId,
        Long contentId,
        String status,
        String reason,
        LocalDateTime requestedAt,
        LocalDateTime processedAt
) {
    public static ApprovalRequestResponse from(ApprovalRequest request) {
        return new ApprovalRequestResponse(
                request.getId(),
                request.getContent().getId(),
                request.getStatus().name(),
                request.getReason(),
                request.getRequestedAt(),
                request.getProcessedAt()
        );
    }
}
