package com.van.cardnews.domain.content.dto.response;

import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.jobhistory.entity.JobHistory;

import java.time.LocalDateTime;

public record ContentCreateResponse(
        Long contentId,
        Long jobHistoryId,
        String status,
        LocalDateTime createdAt
) {

    public static ContentCreateResponse of(Content content, JobHistory jobHistory) {
        return new ContentCreateResponse(
                content.getId(),
                jobHistory.getId(),
                content.getStatus().name(),
                content.getCreatedAt()
        );
    }

    public static ContentCreateResponse of(Content content) {
        return new ContentCreateResponse(
                content.getId(),
                null,
                content.getStatus().name(),
                content.getCreatedAt()
        );
    }
}
