package com.van.cardnews.domain.content.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record ContentImageCropUpdateRequest(

        @NotEmpty(message = "크롭 정보가 없습니다.")
        List<@Valid CropItem> crops

) {

    public record CropItem(

            @NotNull(message = "이미지 ID는 필수입니다.")
            Long imageId,

            @PositiveOrZero(message = "x는 0 이상이어야 합니다.")
            double x,

            @PositiveOrZero(message = "y는 0 이상이어야 합니다.")
            double y,

            @Positive(message = "width는 0보다 커야 합니다.")
            double width,

            @Positive(message = "height는 0보다 커야 합니다.")
            double height

    ) {
    }
}
