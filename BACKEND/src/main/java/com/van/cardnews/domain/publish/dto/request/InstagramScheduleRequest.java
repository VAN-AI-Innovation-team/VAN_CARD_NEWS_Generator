package com.van.cardnews.domain.publish.dto.request;

import java.time.LocalDateTime;

/**
 * 예약 발행 등록 요청입니다. 시각은 Asia/Seoul 기준입니다.
 */
public record InstagramScheduleRequest(
        LocalDateTime scheduledAt,
        String caption
) {
}
