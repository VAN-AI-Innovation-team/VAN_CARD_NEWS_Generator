package com.van.cardnews.global.publish.instagram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Meta 오류 코드 → 실패 유형 매핑을 검증합니다.
 *
 * 이 매핑은 prod 프로필의 {@code InstagramClientImpl}에서만 실행되지만 순수 함수라
 * 합성 입력으로 지금 전부 검증할 수 있습니다. prod로 전환한 순간 처음 실행되는 코드로 남기지 않습니다.
 */
class PublishFailureTest {

    @Test
    @DisplayName("토큰 오류(190)는 재시도 불가로 분류한다")
    void 토큰_오류() {
        assertThat(PublishFailure.ofMetaError(190, 0)).isEqualTo(PublishFailure.TOKEN_EXPIRED);
        assertThat(PublishFailure.TOKEN_EXPIRED.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("호출 한도 코드는 지연 재시도 대상으로 분류한다")
    void 레이트리밋() {
        for (int code : new int[]{4, 17, 32, 613}) {
            assertThat(PublishFailure.ofMetaError(code, 0)).isEqualTo(PublishFailure.RATE_LIMITED);
        }

        assertThat(PublishFailure.RATE_LIMITED.getRetryDelay()).isPositive();
    }

    @Test
    @DisplayName("미디어 fetch 실패 서브코드는 재시도 불가, 컨테이너 만료 서브코드는 재시도 대상이다")
    void 발행_전용_서브코드() {
        assertThat(PublishFailure.ofMetaError(9004, 2207052)).isEqualTo(PublishFailure.IMAGE_UNREACHABLE);
        assertThat(PublishFailure.ofMetaError(1, 2207003)).isEqualTo(PublishFailure.IMAGE_UNREACHABLE);
        assertThat(PublishFailure.IMAGE_UNREACHABLE.isRetryable()).isFalse();

        assertThat(PublishFailure.ofMetaError(1, 2207020)).isEqualTo(PublishFailure.CONTAINER_EXPIRED);
        assertThat(PublishFailure.CONTAINER_EXPIRED.isRetryable()).isTrue();
    }

    /** 분류 실패가 곧 발행 포기가 되면 안 된다. */
    @Test
    @DisplayName("모르는 코드는 재시도 대상인 UNKNOWN으로 떨어진다")
    void 미분류() {
        assertThat(PublishFailure.ofMetaError(0, 0)).isEqualTo(PublishFailure.UNKNOWN);
        assertThat(PublishFailure.ofMetaError(12345, 67890)).isEqualTo(PublishFailure.UNKNOWN);
        assertThat(PublishFailure.UNKNOWN.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("재시도 불가 유형의 지연을 물으면 실패한다 — 재시도 경로에 섞이지 않게 한다")
    void 재시도_불가_유형의_지연() {
        assertThatThrownBy(PublishFailure.TOKEN_EXPIRED::getRetryDelay)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("모든 유형이 사용자에게 보일 한국어 메시지를 가진다")
    void 사용자_메시지() {
        for (PublishFailure failure : PublishFailure.values()) {
            assertThat(failure.getUserMessage()).isNotBlank();
        }
    }
}
