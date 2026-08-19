package com.van.cardnews.global.pipeline;

public record ContentGenerationRequestedEvent(
        Long contentId,
        Long jobHistoryId
) {
}
