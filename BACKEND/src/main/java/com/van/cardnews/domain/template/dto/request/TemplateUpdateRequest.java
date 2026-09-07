package com.van.cardnews.domain.template.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record TemplateUpdateRequest(

        @NotBlank(message = "템플릿 이름은 필수입니다.")
        @Size(max = 100, message = "템플릿 이름은 100자를 초과할 수 없습니다.")
        String name,

        @NotBlank(message = "콘텐츠 유형은 필수입니다.")
        String contentType,

        @Positive(message = "캔버스 가로 크기는 0보다 커야 합니다.")
        int canvasWidth,

        @Positive(message = "캔버스 세로 크기는 0보다 커야 합니다.")
        int canvasHeight,

        @NotNull(message = "레이아웃 정의는 필수입니다.")
        Map<String, Object> layoutDefinition,

        @NotNull(message = "디자인 토큰은 필수입니다.")
        Map<String, Object> designTokens
) {
}
