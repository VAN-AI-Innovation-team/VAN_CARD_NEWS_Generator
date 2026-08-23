package com.van.cardnews.domain.content.dto.response;

import com.van.cardnews.domain.approval.entity.ApprovalRequest;
import com.van.cardnews.domain.content.entity.Content;

import java.time.LocalDateTime;

public record ContentManagementListResponse(
        Long contentId,
        String title,
        LocalDateTime createdAt,
        int cardCount,
        String contentStatus,
        String approvalStatus,
        Long approvalRequestId
) {
    public static ContentManagementListResponse from(
            Content content,
            int cardCount,
            ApprovalRequest approvalRequest
    ) {
        return new ContentManagementListResponse(
                content.getId(),
                content.getTitle(),
                content.getCreatedAt(),
                cardCount,
                content.getStatus().name(),
                approvalRequest != null ? approvalRequest.getStatus().name() : null,
                approvalRequest != null ? approvalRequest.getId() : null
        );
    }
}
