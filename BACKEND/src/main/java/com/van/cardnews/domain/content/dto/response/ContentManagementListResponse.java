package com.van.cardnews.domain.content.dto.response;

import com.van.cardnews.domain.approval.entity.ApprovalRequest;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.publish.entity.PublishRecord;

import java.time.LocalDateTime;

/**
 * 목록 한 줄. 발행 상태는 콘텐츠 상태(PUBLISHED)로는 예약을 구분할 수 없어 최신 발행 건에서 따로 가져온다.
 */
public record ContentManagementListResponse(
        Long contentId,
        String title,
        LocalDateTime createdAt,
        int cardCount,
        String contentStatus,
        String approvalStatus,
        Long approvalRequestId,
        String generationStatus,
        String publishStatus,
        LocalDateTime publishScheduledAt
) {
    public static ContentManagementListResponse from(
            Content content,
            int cardCount,
            ApprovalRequest approvalRequest,
            String generationStatus,
            PublishRecord publishRecord
    ) {
        return new ContentManagementListResponse(
                content.getId(),
                content.getTitle(),
                content.getCreatedAt(),
                cardCount,
                content.getStatus().name(),
                approvalRequest != null ? approvalRequest.getStatus().name() : null,
                approvalRequest != null ? approvalRequest.getId() : null,
                generationStatus,
                publishRecord != null ? publishRecord.getStatus().name() : null,
                publishRecord != null ? publishRecord.getScheduledAt() : null
        );
    }
}
