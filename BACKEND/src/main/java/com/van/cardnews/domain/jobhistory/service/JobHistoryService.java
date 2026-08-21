package com.van.cardnews.domain.jobhistory.service;

import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.jobhistory.dto.response.JobHistoryResponse;
import com.van.cardnews.domain.jobhistory.entity.JobHistory;
import com.van.cardnews.domain.jobhistory.entity.JobType;
import com.van.cardnews.domain.jobhistory.repository.JobHistoryRepository;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobHistoryService {

    private final JobHistoryRepository jobHistoryRepository;

    /**
     * 콘텐츠와 작업 유형을 받아 JobHistory를 생성하고 즉시 DB에 플러시합니다.
     */
    @Transactional
    public JobHistory createJobHistory(
            Content content,
            JobType jobType
    ) {
        JobHistory jobHistory =
                JobHistory.createPending(
                        content,
                        jobType
                );

        return jobHistoryRepository.saveAndFlush(
                jobHistory
        );
    }

    /**
     * 작업 ID를 기준으로 작업 상태 및 결과를 조회합니다.
     */
    @Transactional(readOnly = true)
    public JobHistoryResponse getJobHistory(
            Long jobId
    ) {
        JobHistory jobHistory =
                jobHistoryRepository.findById(jobId)
                        .orElseThrow(() ->
                                new CustomException(
                                        ErrorCode.JOB_HISTORY_NOT_FOUND
                                )
                        );

        return JobHistoryResponse.from(jobHistory);
    }
}
