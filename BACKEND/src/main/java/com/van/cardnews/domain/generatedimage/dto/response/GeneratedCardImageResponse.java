package com.van.cardnews.domain.generatedimage.dto.response;

import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;

import java.util.List;

public record GeneratedCardImageResponse(
        Long id,
        String cardType,
        int cardIndex,
        int sortOrder,
        String imageUrl,
        Integer width,
        Integer height
) {
    public static GeneratedCardImageResponse from(GeneratedCardImage entity) {
        return new GeneratedCardImageResponse(
                entity.getId(),
                entity.getCardType().name(),
                entity.getCardIndex(),
                entity.getSortOrder(),
                entity.getImageUrl(),
                entity.getResolutionWidth(),
                entity.getResolutionHeight()
        );
    }

    public static List<GeneratedCardImageResponse> from(List<GeneratedCardImage> entities) {
        return entities.stream().map(GeneratedCardImageResponse::from).toList();
    }
}
