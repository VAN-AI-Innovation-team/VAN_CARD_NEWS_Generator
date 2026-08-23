package com.van.cardnews.domain.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ContentEditRequest(
        @NotBlank String title,
        @NotBlank String body,
        @NotNull Long templateId,
        List<Long> keepImageIds
) {
}
