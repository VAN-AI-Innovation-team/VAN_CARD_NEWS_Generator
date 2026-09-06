package com.van.cardnews.domain.publish.dto.response;

import com.van.cardnews.domain.publish.entity.PublishRecord;

import java.time.LocalDateTime;

/**
 * 발행 건의 현재 상태입니다.
 *
 * 예약 건의 {@code scheduledAt}과 완료 건의 {@code permalink}를 한 응답에 담아,
 * 화면이 예약/발행을 따로 조회하지 않게 합니다.
 */
public record PublishRecordResponse(
        Long publishRecordId,
        Long contentId,
        String channel,
        String status,
        LocalDateTime scheduledAt,
        String igMediaId,
        String permalink,
        String errorMessage,
        int retryCount,
        LocalDateTime requestedAt,
        LocalDateTime publishedAt
) {

    public static PublishRecordResponse from(PublishRecord record) {
        return new PublishRecordResponse(
                record.getId(),
                record.getContent().getId(),
                record.getChannel(),
                record.getStatus().name(),
                record.getScheduledAt(),
                record.getIgMediaId(),
                record.getPermalink(),
                record.getErrorMessage(),
                record.getRetryCount(),
                record.getRequestedAt(),
                record.getPublishedAt());
    }
}
