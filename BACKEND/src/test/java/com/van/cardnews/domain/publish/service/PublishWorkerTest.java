package com.van.cardnews.domain.publish.service;

import com.van.cardnews.domain.publish.dto.response.PublishWorkerResponse;
import com.van.cardnews.domain.publish.entity.PublishRecord;
import com.van.cardnews.domain.publish.entity.PublishStatus;
import com.van.cardnews.domain.publish.repository.PublishRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 워커의 실행 순서와 선점 판정을 검증한다.
 *
 * 여기서 다루는 것은 <b>워커가 선점 결과를 따르는가</b>이고,
 * 그 선점이 동시 실행에서도 한 워커만 통과시키는가(조건부 UPDATE의 원자성)는 DB가 있어야
 * 판정되므로 {@code PublishWorkerIntegrationTest}가 맡는다. 둘이 합쳐져야 "두 번 돌려도 1회"가 된다.
 */
class PublishWorkerTest {

    private static final int BATCH_SIZE = 5;

    private PublishRecordRepository publishRecordRepository;
    private InstagramPublishService instagramPublishService;
    private PublishWorker worker;

    @BeforeEach
    void setUp() {
        publishRecordRepository = mock(PublishRecordRepository.class);
        instagramPublishService = mock(InstagramPublishService.class);

        worker = new PublishWorker(publishRecordRepository, instagramPublishService);

        ReflectionTestUtils.setField(worker, "batchSize", BATCH_SIZE);
        ReflectionTestUtils.setField(worker, "stuckThresholdMinutes", 10L);
        ReflectionTestUtils.setField(worker, "graceMinutes", 60L);
    }

    private PublishRecord due(long id) {
        PublishRecord record = mock(PublishRecord.class);
        when(record.getId()).thenReturn(id);
        return record;
    }

    private void givenDue(PublishRecord... records) {
        when(publishRecordRepository.findByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                any(), any(), any())).thenReturn(List.of(records));
    }

    private void givenExecuted(long id, PublishStatus status) {
        PublishRecord result = mock(PublishRecord.class);
        when(result.getStatus()).thenReturn(status);
        when(instagramPublishService.execute(id)).thenReturn(result);
    }

    @Test
    void 선점에_성공한_건만_발행한다() {
        givenDue(due(1L), due(2L));
        when(publishRecordRepository.claim(eq(1L), any())).thenReturn(1);
        when(publishRecordRepository.claim(eq(2L), any())).thenReturn(0);
        givenExecuted(1L, PublishStatus.SUCCESS);

        PublishWorkerResponse response = worker.runDue();

        assertThat(response.published()).isEqualTo(1);
        assertThat(response.skipped()).isEqualTo(1);
        verify(instagramPublishService).execute(1L);
        verify(instagramPublishService, never()).execute(2L);
    }

    @Test
    void 회수와_유예_정리를_발행보다_먼저_돌린다() {
        givenDue();
        when(publishRecordRepository.failStuck(any(), anyString())).thenReturn(2);
        when(publishRecordRepository.failExpired(any(), anyString())).thenReturn(3);

        PublishWorkerResponse response = worker.runDue();

        assertThat(response.stuckRecovered()).isEqualTo(2);
        assertThat(response.expired()).isEqualTo(3);

        // 유예 초과 건을 먼저 FAILED로 내려야 그 건이 이번 회차의 발행 대상에 섞이지 않는다
        InOrder order = inOrder(publishRecordRepository);
        order.verify(publishRecordRepository).failStuck(any(), anyString());
        order.verify(publishRecordRepository).failExpired(any(), anyString());
        order.verify(publishRecordRepository)
                .findByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(any(), any(), any());
    }

    @Test
    void 한_회차에_집는_건수는_상한을_넘지_않는다() {
        givenDue();

        worker.runDue();

        verify(publishRecordRepository).findByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                eq(List.of(PublishStatus.SCHEDULED, PublishStatus.PENDING)),
                any(LocalDateTime.class),
                eq(Limit.of(BATCH_SIZE)));
    }

    @Test
    void 한_건이_예외로_죽어도_나머지를_계속_처리한다() {
        givenDue(due(1L), due(2L));
        when(publishRecordRepository.claim(anyLong(), any())).thenReturn(1);
        when(instagramPublishService.execute(1L)).thenThrow(new RuntimeException("DB 연결 끊김"));
        givenExecuted(2L, PublishStatus.SUCCESS);

        PublishWorkerResponse response = worker.runDue();

        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.published()).isEqualTo(1);
    }

    @Test
    void 발행에_실패한_건은_실패로_센다() {
        givenDue(due(1L));
        when(publishRecordRepository.claim(anyLong(), any())).thenReturn(1);
        givenExecuted(1L, PublishStatus.FAILED);

        PublishWorkerResponse response = worker.runDue();

        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.published()).isZero();
    }
}
