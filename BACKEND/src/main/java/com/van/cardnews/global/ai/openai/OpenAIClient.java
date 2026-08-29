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
}
