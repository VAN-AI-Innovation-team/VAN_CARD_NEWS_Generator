package com.van.cardnews.domain.content.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record ContentPreviewUpdateRequest(

        @NotNull(message = "카드 구성 결과는 필수입니다.")
        JsonNode cardGenerationResult

) {
}
