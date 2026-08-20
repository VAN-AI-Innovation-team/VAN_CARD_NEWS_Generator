package com.van.cardnews.domain.content.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record CardImagePlacementUpdateRequest(

        @NotEmpty(message = "카드 이미지 배치 정보가 없습니다.")
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

    public record CropArea(
            double x,
            double y,
            double width,
            double height
    ) {
    }
}
