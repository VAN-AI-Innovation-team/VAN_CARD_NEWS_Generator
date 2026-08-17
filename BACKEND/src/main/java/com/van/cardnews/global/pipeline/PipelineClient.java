package com.van.cardnews.global.pipeline;

public interface PipelineClient {

    /**
     * 생성 파이프라인 실행을 트리거합니다.
     * @param contentId     생성 대상 콘텐츠 ID
     * @param workHistoryId 이번 실행에 대응하는 작업 이력 ID
     */
    void triggerGeneration(Long contentId, Long workHistoryId);
}
