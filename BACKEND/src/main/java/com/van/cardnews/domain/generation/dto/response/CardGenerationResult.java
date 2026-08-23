package com.van.cardnews.domain.generation.dto.response;

import java.util.List;

public record CardGenerationResult(
        CoverContent cover,
        List<ContentCard> content,
        ClosingContent closing
) {

    public record CoverContent(
            String title,
            String highlight,
            String date,
            String location,
            Long imageId,
            CropArea cropArea
    ) {
    }

    public record ContentCard(
            String title,
            String body,
            String highlight,
            String date,
            String location,
            Long imageId,
            CropArea cropArea
    ) {
    }

    public record ClosingContent(
            String cta,
            Long imageId,
            CropArea cropArea
    ) {
    }

    /**
     * 원본 이미지 기준 픽셀 단위 크롭 영역입니다.
     */
    public record CropArea(
            double x,
            double y,
            double width,
            double height
    ) {
    }
}
