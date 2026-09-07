package com.van.cardnews.domain.content.entity;

/**
 * 콘텐츠의 "발행 상태"를 나타냅니다. job_histories.status(작업 진행 상태: PENDING 등)와는
 * 완전히 다른 개념이니 혼동하지 마세요.
 *   - DRAFT     : 작성/생성 중 (기본값)
 *   - PUBLISHED : 발행 완료, 사용자에게 노출
 *   - ARCHIVED  : 보관 처리 (하드 삭제 대신 이 상태로 전환하는 소프트 삭제 용도)
 */
public enum ContentStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
