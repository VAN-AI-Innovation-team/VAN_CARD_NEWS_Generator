package com.van.cardnews.global.publish.instagram;

import java.time.Duration;

/**
 * 발행 실패의 원인 분류입니다. 원인별 재시도 정책과 사용자에게 보일 한국어 메시지를 함께 들고 있습니다.
 *
 * 재시도 여부를 호출부 곳곳의 조건문이 아니라 이 enum이 결정합니다.
 * 실패는 발행 경로 여러 지점(자격 조회·자식 컨테이너·상태 폴링·발행)에서 나오지만 처리 정책은 원인으로만 갈립니다.
 *
 * {@code retryDelay}가 {@code null}이면 자동 재시도 대상이 아닙니다 — 사람이 무언가를 고쳐야 풀리는 실패입니다.
 */
public enum PublishFailure {

    /** 재인증 없이는 몇 번을 다시 호출해도 같은 결과다. 운영자에게 알리고 멈춘다. */
    TOKEN_EXPIRED(
            "인스타그램 액세스 토큰이 만료되었습니다. 계정 관리자의 재인증이 필요합니다.",
            null),

    /** 카드 수가 캐러셀 제약(1~10장)을 벗어났다. 콘텐츠가 그대로인 한 다시 올려도 같은 결과다. */
    INVALID_CARDS(
            "발행할 카드 이미지가 캐러셀 제약(1~10장)에 맞지 않습니다. 카드 생성 결과를 확인해 주세요.",
            null),

    /** 스토리지 쪽 문제다. 원인을 확인하지 않고 다시 올리면 같은 실패를 반복한다. */
    IMAGE_UNREACHABLE(
            "인스타그램이 카드 이미지를 가져가지 못했습니다. 이미지 저장소 상태를 확인한 뒤 다시 요청해 주세요.",
            null),

    /**
     * 한도는 24시간 이동 윈도우(게시 100건)라 회복까지 시간이 걸린다. 즉시 재시도는 한도만 더 깎는다.
     * 상한 횟수 안에 회복되지 않으면 FAILED로 남고 사용자가 다시 요청한다.
     */
    RATE_LIMITED(
            "인스타그램 발행 한도를 초과했습니다. 한도가 회복되면 자동으로 다시 시도합니다.",
            Duration.ofMinutes(60)),

    /** 컨테이너는 24시간에 만료된다. 재시도는 만료된 것을 되살리지 않고 처음부터 새로 만든다. */
    CONTAINER_EXPIRED(
            "업로드 컨테이너가 만료되어 발행하지 못했습니다. 새 컨테이너로 다시 시도합니다.",
            Duration.ofMinutes(5)),

    CONTAINER_ERROR(
            "인스타그램이 업로드를 처리하지 못했습니다. 잠시 후 다시 시도합니다.",
            Duration.ofMinutes(5)),

    /** 분류되지 않은 실패. 원인을 모른다는 이유로 발행을 포기하지는 않는다. */
    UNKNOWN(
            "발행에 실패했습니다. 잠시 후 다시 시도합니다.",
            Duration.ofMinutes(5));

    private final String userMessage;
    private final Duration retryDelay;

    PublishFailure(String userMessage, Duration retryDelay) {
        this.userMessage = userMessage;
        this.retryDelay = retryDelay;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public boolean isRetryable() {
        return retryDelay != null;
    }

    public Duration getRetryDelay() {
        if (retryDelay == null) {
            throw new IllegalStateException(name() + "은(는) 자동 재시도 대상이 아닙니다.");
        }

        return retryDelay;
    }

    /**
     * Meta 오류 응답의 코드를 실패 유형으로 옮깁니다.
     *
     * 코드는 Graph API 공통(190 토큰, 4·17·32·613 호출 한도)이고,
     * 서브코드는 콘텐츠 발행 전용입니다(2207003 미디어 다운로드 타임아웃, 2207052 미디어 fetch 실패,
     * 2207020 컨테이너 만료).
     *
     * 모르는 코드는 {@link #UNKNOWN}으로 둡니다. 분류 실패가 곧 발행 포기가 되면 안 됩니다.
     */
    public static PublishFailure ofMetaError(int code, int subcode) {
        if (code == 190) {
            return TOKEN_EXPIRED;
        }
        if (code == 4 || code == 17 || code == 32 || code == 613) {
            return RATE_LIMITED;
        }

        return switch (subcode) {
            case 2207003, 2207052 -> IMAGE_UNREACHABLE;
            case 2207020 -> CONTAINER_EXPIRED;
            default -> UNKNOWN;
        };
    }
}
