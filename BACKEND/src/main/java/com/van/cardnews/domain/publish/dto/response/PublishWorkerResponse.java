package com.van.cardnews.domain.publish.dto.response;

/**
 * 워커 1회 실행 결과입니다.
 *
 * Cloud Scheduler는 응답 본문을 읽지 않지만, 로그·수동 호출로 워커가 무엇을 했는지 볼 수 있어야 합니다.
 * {@code skipped}는 다른 워커가 먼저 선점해 이번 회차가 건드리지 않은 건수입니다 — 0이 아니어도 정상입니다.
 */
public record PublishWorkerResponse(
        int published,
        int failed,
        int skipped,
        int stuckRecovered,
        int expired
) {
}
