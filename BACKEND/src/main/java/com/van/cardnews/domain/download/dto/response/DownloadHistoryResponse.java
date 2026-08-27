package com.van.cardnews.domain.download.dto.response;

import com.van.cardnews.domain.download.entity.DownloadHistory;

import java.time.LocalDateTime;

public record DownloadHistoryResponse(
        Long downloadHistoryId,
        Long contentId,
        String channel,
        String downloadType,
        String result,
        int retryCount,
        Long imageId,
        String errorMessage,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {

    public static DownloadHistoryResponse from(DownloadHistory history) {
        return new DownloadHistoryResponse(
                history.getId(),
                history.getContent().getId(),
                history.getChannel(),
                history.getDownloadType().name(),
                history.getResult().name(),
                history.getRetryCount(),
                history.getImageId(),
                history.getErrorMessage(),
                history.getRequestedAt(),
                history.getCompletedAt()
        );
    }
}
