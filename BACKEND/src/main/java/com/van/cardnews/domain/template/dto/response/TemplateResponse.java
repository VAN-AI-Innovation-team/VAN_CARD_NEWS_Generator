package com.van.cardnews.domain.template.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.van.cardnews.domain.template.entity.Template;

public record TemplateResponse(
        Long id,
        String code,
        String name,
        String contentType,
        CanvasResponse canvas,
        JsonNode layout,
        JsonNode designTokens,
        boolean isActive,
        int version
) {

    public static TemplateResponse from(Template template) {
        return new TemplateResponse(
                template.getId(),
                template.getCode(),
                template.getName(),
                template.getContentType().getValue(),
                new CanvasResponse(
                        template.getCanvasWidth(),
                        template.getCanvasHeight()
                ),
                template.getLayoutDefinition(),
                template.getDesignTokens(),
                template.isActive(),
                template.getVersion()
        );
    }

    public record CanvasResponse(
            int width,
            int height
    ) {
    }
}
