package com.van.cardnews.domain.content.dto.request;

import jakarta.validation.constraints.NotNull;

public record ContentTemplateUpdateRequest(
        @NotNull(message = "템플릿 ID는 필수입니다.")
        Long templateId
) {}
