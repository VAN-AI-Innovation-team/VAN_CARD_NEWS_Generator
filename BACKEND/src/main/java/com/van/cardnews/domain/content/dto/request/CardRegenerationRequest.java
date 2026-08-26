package com.van.cardnews.domain.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CardRegenerationRequest(
        @NotBlank(message = "카드 유형은 필수입니다.")
        String cardType,
        @NotNull(message = "카드 인덱스는 필수입니다.")
        Integer cardIndex,
        @NotBlank(message = "재작성 요청은 필수입니다.")
        String instruction
) {}
