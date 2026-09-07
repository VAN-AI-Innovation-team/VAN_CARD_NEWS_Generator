package com.van.cardnews.global.ai.higgsfield.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record HiggsfieldGenerationRequest(
        JsonNode cardGenerationResult,   // Content.cardGenerationResult (OpenAI 결과: 표지/본문/마무리 텍스트 구성)
        JsonNode resolvedImagePlacements, // 카드별 원본 이미지 실제 URL + crop 정보 (4-5단계에서 조립)
        JsonNode layoutDefinition,       // Template.layoutDefinition
        JsonNode designTokens,           // Template.designTokens
        int canvasWidth,                 // Template.canvasWidth (단일 카드 기준)
        int canvasHeight                 // Template.canvasHeight (단일 카드 기준)
) {}
