package com.van.cardnews.domain.jobhistory.dto.response;

import com.van.cardnews.domain.jobhistory.entity.JobHistory;
import com.van.cardnews.domain.jobhistory.entity.JobStatus;

import java.time.LocalDateTime;

public record JobHistoryResponse(
        Long jobId,
        JobStatus status,
        String resultUrl,
        String errorMessage,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {

    public static JobHistoryResponse from(JobHistory jobHistory) {
        return new JobHistoryResponse(
                jobHistory.getId(),
                jobHistory.getStatus(),
                jobHistory.getResultUrl(),
                jobHistory.getErrorMessage(),
                jobHistory.getRequestedAt(),
                jobHistory.getCompletedAt()
        );
    }
}
