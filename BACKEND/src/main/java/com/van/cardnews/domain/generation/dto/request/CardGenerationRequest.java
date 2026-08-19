package com.van.cardnews.domain.generation.dto.request;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record CardGenerationRequest(
        String title,
        String body,
        String contentType,
        JsonNode layoutDefinition,
        List<InputImage> images
) {

    public record InputImage(
            Long imageId,
            String mediaType,
            String base64Data
    ) {
    }
}
