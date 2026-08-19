package com.van.cardnews.global.ai.claude;

import com.van.cardnews.domain.generation.dto.request.CardGenerationRequest;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;

public interface ClaudeClient {

    CardGenerationResult generateCardContent(
            CardGenerationRequest request
    );
}
