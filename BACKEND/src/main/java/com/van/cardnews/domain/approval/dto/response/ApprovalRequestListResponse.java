package com.van.cardnews.domain.approval.dto.response;

import com.van.cardnews.domain.approval.entity.ApprovalRequest;

import java.time.LocalDateTime;

public record ApprovalRequestListResponse(
        Long approvalRequestId,
        Long contentId,
        String title,
        String status,
        String reason,
        LocalDateTime requestedAt,
        LocalDateTime processedAt
) {

    public static ApprovalRequestListResponse from(
            ApprovalRequest request
    ) {
        return new ApprovalRequestListResponse(
                request.getId(),
                request.getContent().getId(),
                request.getContent().getTitle(),
                request.getStatus().name(),
                request.getReason(),
                request.getRequestedAt(),
                request.getProcessedAt()
        );
    }
}
