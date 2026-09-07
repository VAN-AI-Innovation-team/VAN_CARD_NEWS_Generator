package com.van.cardnews.domain.publish.entity;

import com.van.cardnews.global.publish.instagram.PublishFailure;
import com.van.cardnews.global.time.KoreaTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 발행 건의 상태 전이 규칙을 검증합니다.
 * content는 전이 로직이 건드리지 않으므로 null로 둡니다.
 */
class PublishRecordTest {

    private static final int MAX_RETRY_COUNT = 3;

    @Test
    @DisplayName("예약 건은 SCHEDULED, 즉시 발행 건은 PENDING으로 시작한다")
    void 생성_시_초기_상태() {
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);

        assertThat(PublishRecord.schedule(null, "INSTAGRAM", "캡션", tomorrow).getStatus())
                .isEqualTo(PublishStatus.SCHEDULED);
        assertThat(PublishRecord.publishNow(null, "INSTAGRAM", "캡션").getStatus())
                .isEqualTo(PublishStatus.PENDING);
    }

    @Test
    @DisplayName("워커 선점 시 PROCESSING과 선점 시각이 기록된다")
    void 선점() {
        PublishRecord record = PublishRecord.publishNow(null, "INSTAGRAM", "캡션");

        record.markProcessing();

        assertThat(record.getStatus()).isEqualTo(PublishStatus.PROCESSING);
        assertThat(record.getProcessingStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("성공 시 미디어 ID와 발행 시각이 남고 이전 에러는 지워진다")
    void 성공() {
        PublishRecord record = PublishRecord.publishNow(null, "INSTAGRAM", "캡션");
        record.fail(PublishFailure.UNKNOWN, MAX_RETRY_COUNT);

        record.markSuccess("ig-123", "https://instagram.com/p/abc");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.SUCCESS);
        assertThat(record.getIgMediaId()).isEqualTo("ig-123");
        assertThat(record.getPublishedAt()).isNotNull();
        assertThat(record.getErrorMessage()).isNull();
        assertThat(record.getFailureType()).isNull();
    }

    @Test
    @DisplayName("재시도 가능한 실패는 원인별 지연만큼 뒤로 재예약되고 retryCount가 증가한다")
    void 재시도_가능한_실패() {
        PublishRecord record = PublishRecord.publishNow(null, "INSTAGRAM", "캡션");
        record.markProcessing();
        LocalDateTime before = KoreaTime.now();

        record.fail(PublishFailure.RATE_LIMITED, MAX_RETRY_COUNT);

        assertThat(record.getStatus()).isEqualTo(PublishStatus.SCHEDULED);
        assertThat(record.getRetryCount()).isEqualTo(1);
        assertThat(record.getProcessingStartedAt()).isNull();
        assertThat(record.getFailureType()).isEqualTo(PublishFailure.RATE_LIMITED);
        assertThat(record.getErrorMessage()).isEqualTo(PublishFailure.RATE_LIMITED.getUserMessage());

        // 즉시 재시도하지 않는다 — 쿼터 회복까지 기다린 시각으로 밀려 있어야 한다
        assertThat(record.getScheduledAt())
                .isAfterOrEqualTo(before.plus(PublishFailure.RATE_LIMITED.getRetryDelay()));
    }

    @Test
    @DisplayName("재시도 불가한 실패는 재예약 없이 즉시 FAILED로 멈춘다")
    void 재시도_불가한_실패() {
        PublishRecord record = PublishRecord.publishNow(null, "INSTAGRAM", "캡션");
        record.markProcessing();

        record.fail(PublishFailure.TOKEN_EXPIRED, MAX_RETRY_COUNT);

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getRetryCount()).isZero();
        assertThat(record.getFailureType()).isEqualTo(PublishFailure.TOKEN_EXPIRED);
        assertThat(record.getErrorMessage()).isEqualTo(PublishFailure.TOKEN_EXPIRED.getUserMessage());
    }

    @Test
    @DisplayName("재시도 상한을 넘기면 더 이상 재예약하지 않는다")
    void 재시도_상한() {
        PublishRecord record = PublishRecord.publishNow(null, "INSTAGRAM", "캡션");

        for (int attempt = 0; attempt < MAX_RETRY_COUNT; attempt++) {
            record.fail(PublishFailure.UNKNOWN, MAX_RETRY_COUNT);
            assertThat(record.getStatus()).isEqualTo(PublishStatus.SCHEDULED);
        }

        record.fail(PublishFailure.UNKNOWN, MAX_RETRY_COUNT);

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getRetryCount()).isEqualTo(MAX_RETRY_COUNT);
    }

    @Test
    @DisplayName("이미 발행된 건은 취소할 수 없다")
    void 취소() {
        PublishRecord scheduled = PublishRecord.schedule(null, "INSTAGRAM", "캡션", LocalDateTime.now());
        scheduled.cancel();
        assertThat(scheduled.getStatus()).isEqualTo(PublishStatus.CANCELED);

        PublishRecord published = PublishRecord.publishNow(null, "INSTAGRAM", "캡션");
        published.markSuccess("ig-123", null);

        assertThatThrownBy(published::cancel)
                .isInstanceOf(IllegalStateException.class);
    }
}
