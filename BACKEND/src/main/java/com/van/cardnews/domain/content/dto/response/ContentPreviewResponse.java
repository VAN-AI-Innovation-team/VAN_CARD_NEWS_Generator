package com.van.cardnews.domain.content.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.entity.ContentImage;
import com.van.cardnews.domain.approval.entity.ApprovalRequest;

import java.util.List;

public record ContentPreviewResponse(
        Long contentId,
        String title,
        String body,
        String status,
        String approvalStatus,
        TemplatePreview template,
        JsonNode cardGenerationResult,
        JsonNode cardImagePlacements,
        List<ImagePreview> images
) {

    public static ContentPreviewResponse from(Content content) {
        return from(content, null);
    }

    public static ContentPreviewResponse from(
            Content content,
            ApprovalRequest approvalRequest
    ) {
        return new ContentPreviewResponse(
                content.getId(),
                content.getTitle(),
                content.getBody(),
                content.getStatus().name(),
                approvalRequest != null
                        ? approvalRequest.getStatus().name()
                        : null,
                TemplatePreview.from(content),
                content.getCardGenerationResult(),
                content.getCardImagePlacements(),
                content.getImages()
                        .stream()
                        .map(ImagePreview::from)
                        .toList()
        );
    }

    public record TemplatePreview(
            Long id,
            String code,
            String name,
            String contentType,
            int version,
            int canvasWidth,
            int canvasHeight,
            JsonNode layout,
            JsonNode designTokens
    ) {

        public static TemplatePreview from(Content content) {
            var template = content.getTemplate();

            return new TemplatePreview(
                    template.getId(),
                    template.getCode(),
                    template.getName(),
                    template.getContentType().getValue(),
                    template.getVersion(),
                    template.getCanvasWidth(),
                    template.getCanvasHeight(),
                    template.getLayoutDefinition(),
                    template.getDesignTokens()
            );
        }
    }

    public record ImagePreview(
            Long id,
            String imageUrl,
            String cropArea,
            String generatedImageUrl,
            int sortOrder
    ) {

        public static ImagePreview from(ContentImage image) {
            return new ImagePreview(
                    image.getId(),
                    image.getImageUrl(),
                    image.getCropArea(),
                    image.getGeneratedImageUrl(),
                    image.getSortOrder()
            );
        }
    }
}
