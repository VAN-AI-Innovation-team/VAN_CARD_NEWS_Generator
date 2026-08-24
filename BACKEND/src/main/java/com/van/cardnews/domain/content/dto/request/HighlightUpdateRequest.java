package com.van.cardnews.domain.content.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record HighlightUpdateRequest(
        @NotBlank(message = "강조 문구는 필수입니다.")
        String highlight,

        @NotNull(message = "카드 유형은 필수입니다.")
        CardType cardType,

        @NotNull(message = "카드 인덱스는 필수입니다.")
        @Min(value = 0, message = "카드 인덱스는 0 이상이어야 합니다.")
        Integer cardIndex
) {
    public enum CardType {
        COVER,
        CONTENT
    }
}
