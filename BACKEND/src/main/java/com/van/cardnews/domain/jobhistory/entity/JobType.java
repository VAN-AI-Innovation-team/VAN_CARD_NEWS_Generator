package com.van.cardnews.domain.jobhistory.entity;

/**
 * job_histories.job_type CHECK 제약이 네 가지 값으로 확장되었습니다.
 *   - COPY_GENERATION  : 콘텐츠 문구(카피)만 생성
 *   - IMAGE_GENERATION : 이미지만 생성
 *   - FULL_PIPELINE    : 문구+이미지 전체 파이프라인 실행
 *   - INSTAGRAM_PUBLISH: 인스타그램 캐러셀 발행 (성공 건만 기록된다)
 */
public enum JobType {
    COPY_GENERATION,
    IMAGE_GENERATION,
    FULL_PIPELINE,
    INSTAGRAM_PUBLISH
}
