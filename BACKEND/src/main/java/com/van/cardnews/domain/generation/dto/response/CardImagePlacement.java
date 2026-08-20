package com.van.cardnews.domain.generation.dto.response;

public record CardImagePlacement(
        String cardType,
        Integer cardIndex,
        Long imageId,
        CropArea cropArea
) {

    public record CropArea(
            double x,
            double y,
            double width,
            double height
    ) {
    }
}
