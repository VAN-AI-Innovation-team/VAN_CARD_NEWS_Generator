package com.van.cardnews.domain.content.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record CardImagePlacementUpdateRequest(

        @NotNull(message = "카드 이미지 배치 정보는 필수입니다.")
        List<@Valid Placement> placements

) {

    public record Placement(

            @NotNull(message = "카드 유형은 필수입니다.")
            String cardType,

            @NotNull(message = "카드 인덱스는 필수입니다.")
            @PositiveOrZero(message = "카드 인덱스는 0 이상이어야 합니다.")
            Integer cardIndex,

            @NotNull(message = "이미지 ID는 필수입니다.")
            Long imageId,

            @NotNull(message = "크롭 영역은 필수입니다.")
            @Valid CropArea cropArea
    ) {
    }

    /**
     * 원본 이미지 기준 백분율 좌표입니다.
     *
     * x/y: 좌상단 기준 시작점
     * width/height: 선택 영역 크기
     */
    public record CropArea(
            @DecimalMin(value = "0.0", message = "크롭 x는 0 이상이어야 합니다.")
            @DecimalMax(value = "100.0", message = "크롭 x는 100 이하여야 합니다.")
            double x,

            @DecimalMin(value = "0.0", message = "크롭 y는 0 이상이어야 합니다.")
            @DecimalMax(value = "100.0", message = "크롭 y는 100 이하여야 합니다.")
            double y,

            @DecimalMin(value = "0.1", message = "크롭 width는 0보다 커야 합니다.")
            @DecimalMax(value = "100.0", message = "크롭 width는 100 이하여야 합니다.")
            double width,

            @DecimalMin(value = "0.1", message = "크롭 height는 0보다 커야 합니다.")
            @DecimalMax(value = "100.0", message = "크롭 height는 100 이하여야 합니다.")
            double height
    ) {
    }
}
