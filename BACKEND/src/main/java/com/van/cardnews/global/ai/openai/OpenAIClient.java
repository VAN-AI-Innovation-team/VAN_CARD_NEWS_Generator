package com.van.cardnews.global.ai.openai;

import com.van.cardnews.domain.generation.dto.request.CardGenerationRequest;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;

public interface OpenAIClient {

    CardGenerationResult generateCardContent(
            CardGenerationRequest request
    );

    CardGenerationResult regenerateCardContent(
            CardGenerationRequest request,
            CardGenerationResult currentResult,
            String cardType,
            int cardIndex,
            String instruction
    );

    /**
     * 이 생성기가 문구를 만들어내지 못하고 자리만 채워 넣은 값인지 판정합니다.
     *
     * 판별 책임이 생성기에 있는 이유는, 무엇이 플레이스홀더인지 아는 것은 그것을 넣은 쪽뿐이기 때문입니다.
     * 캡션 조립부가 Mock의 고정 문구를 알면 그 규칙은 prod에서 정반대로 틀립니다 —
     * 실제로 생성된 highlight·title·cta는 걸러낼 대상이 아니라 캡션의 주재료입니다.
     *
     * @param fieldPath {@code cover.highlight}처럼 카드 유형과 필드로 이루어진 경로
     */
    boolean isPlaceholder(String fieldPath, String value);
}
