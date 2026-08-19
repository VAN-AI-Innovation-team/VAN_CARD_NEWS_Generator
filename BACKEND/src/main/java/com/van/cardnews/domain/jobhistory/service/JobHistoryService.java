package com.van.cardnews.domain.jobhistory.service;

import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.jobhistory.entity.JobHistory;
import com.van.cardnews.domain.jobhistory.entity.JobType;
import com.van.cardnews.domain.jobhistory.repository.JobHistoryRepository;
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
    public JobHistory createJobHistory(Content content, JobType jobType) {
        // 만약 JobHistory 엔티티에 createPending 대신 다른 생성 메서드가 있다면 그에 맞게 수정해주세요.
        JobHistory jobHistory = JobHistory.createPending(content, jobType);

        return jobHistoryRepository.saveAndFlush(jobHistory);
    }
}
