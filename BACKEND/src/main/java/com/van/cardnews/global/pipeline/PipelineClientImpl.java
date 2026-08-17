package com.van.cardnews.global.pipeline;

import com.van.cardnews.domain.jobhistory.entity.JobHistory;
import com.van.cardnews.domain.jobhistory.repository.JobHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineClientImpl implements PipelineClient {

    private final JobHistoryRepository jobHistoryRepository;

    /**
     * 현재는 실제 Claude/Higgsfield API를 호출하지 않고,
     * "요청이 파이프라인 단계로 정상 전달되었다"는 것을 WorkHistory 상태 변경으로 기록합니다.
     * 실 연동 시 이 메서드 내부만 교체(WebClient로 외부 API 호출)하면 됩니다.
     */
    @Async
    @Override
    @Transactional
    public void triggerGeneration(Long contentId, Long jobHistoryId) {
        log.info("[Pipeline] 생성 파이프라인 트리거 - contentId={}, jobHistoryId={}", contentId, jobHistoryId);

        JobHistory jobHistory = jobHistoryRepository.findById(jobHistoryId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 작업 이력입니다. id=" + jobHistoryId));

        jobHistory.markProcessing();
        log.info("[Pipeline] 작업 이력 상태 변경 완료 - jobHistoryId={}, status={}",
                jobHistoryId, jobHistory.getStatus());

        // TODO: 이후 이슈에서 Claude/Higgsfield API 실제 호출 및
        //       완료 시 workHistory.markCompleted() / 실패 시 markFailed(message) 처리
    }
}
