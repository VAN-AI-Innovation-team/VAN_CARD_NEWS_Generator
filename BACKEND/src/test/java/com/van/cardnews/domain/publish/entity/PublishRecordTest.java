package com.van.cardnews.domain.publish.entity;

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
        record.markFailed("일시 오류");

        record.markSuccess("ig-123", "https://instagram.com/p/abc");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.SUCCESS);
        assertThat(record.getIgMediaId()).isEqualTo("ig-123");
        assertThat(record.getPublishedAt()).isNotNull();
        assertThat(record.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("실패한 건만 재시도할 수 있고 재시도하면 retryCount가 증가한다")
    void 재시도() {
        PublishRecord record = PublishRecord.publishNow(null, "INSTAGRAM", "캡션");
        record.markProcessing();
        record.markFailed("API 오류");

        record.retry();

        assertThat(record.getStatus()).isEqualTo(PublishStatus.PENDING);
        assertThat(record.getRetryCount()).isEqualTo(1);
        assertThat(record.getProcessingStartedAt()).isNull();

        assertThatThrownBy(record::retry)
                .isInstanceOf(IllegalStateException.class);
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
