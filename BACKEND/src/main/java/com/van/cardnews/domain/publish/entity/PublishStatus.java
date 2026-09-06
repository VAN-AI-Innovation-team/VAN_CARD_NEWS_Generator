package com.van.cardnews.domain.publish.entity;

/**
 * 발행 건의 진행 상태입니다.
 *
 *   - SCHEDULED  : 예약 등록됨. scheduled_at이 도래하면 워커가 집어갑니다.
 *   - PENDING    : 즉시 발행 요청됨. 아직 워커가 선점하지 않았습니다.
 *   - PROCESSING : 워커가 선점해 외부 API 호출 중입니다.
 *   - SUCCESS    : 외부 채널 게시 완료.
 *   - FAILED     : 게시 실패. 재시도 대상입니다.
 *   - CANCELED   : 발행 전에 사용자가 취소했습니다.
 */
public enum PublishStatus {
    SCHEDULED,
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELED
}
