package com.van.cardnews.global.pipeline;

import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.repository.ContentRepository;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;
import com.van.cardnews.domain.generation.service.CardGenerationService;
import com.van.cardnews.domain.jobhistory.entity.JobHistory;
import com.van.cardnews.domain.jobhistory.repository.JobHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineClientImpl implements PipelineClient {

    private final JobHistoryRepository jobHistoryRepository;
    private final ContentRepository contentRepository;
    private final CardGenerationService cardGenerationService;

    /**
     * Content와 JobHistory 저장 트랜잭션이 COMMIT된 이후
     * 새로운 트랜잭션으로 생성 파이프라인을 실행합니다.
     */
    @Async
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleGenerationRequested(
            ContentGenerationRequestedEvent event
    ) {
        triggerGeneration(
                event.contentId(),
                event.jobHistoryId()
        );
    }

    /**
     * 실제 카드뉴스 생성 파이프라인입니다.
     *
     * handleGenerationRequested()에서 이미
     * REQUIRES_NEW 트랜잭션이 열린 상태이므로
     * 이 메서드의 @Transactional은 같은 트랜잭션에 참여합니다.
     */
    @Override
    @Transactional
    public void triggerGeneration(
            Long contentId,
            Long jobHistoryId
    ) {
        log.info(
                "[Pipeline] 생성 파이프라인 시작 - contentId={}, jobHistoryId={}",
                contentId,
                jobHistoryId
        );

        JobHistory jobHistory =
                jobHistoryRepository
                        .findById(jobHistoryId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "존재하지 않는 작업 이력입니다. id="
                                                + jobHistoryId
                                )
                        );

        try {
            jobHistory.markProcessing();

            Content content =
                    contentRepository
                            .findByIdWithImages(contentId)
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "존재하지 않는 콘텐츠입니다. id="
                                                    + contentId
                                    )
                            );

            CardGenerationResult result =
                    cardGenerationService.generate(
                            content
                    );

            log.info(
                    "[Pipeline] 카드 구성 완료 - contentId={}, contentCards={}",
                    contentId,
                    result.content().size()
            );

            /*
             * 현재 #8에서는 카드 구성 결과 생성까지 수행합니다.
             *
             * 실제 이미지 파일 생성 및 JobHistory.COMPLETED 처리는
             * #9 이후 단계에서 연결합니다.
             */

        } catch (Exception e) {
            log.error(
                    "[Pipeline] 생성 파이프라인 실패 - contentId={}, jobHistoryId={}",
                    contentId,
                    jobHistoryId,
                    e
            );

            jobHistory.markFailed(
                    e.getMessage() != null
                            ? e.getMessage()
                            : "카드뉴스 생성 중 오류가 발생했습니다."
            );
        }
    }
}
