package com.van.cardnews.domain.template.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 신규 템플릿 등록 요청
 *
 * code는 클라이언트가 지정하지 않습니다.
 * 서버가 contentType에 따라 {접두문자}{일련번호} 규칙으로
 * 자동 확정합니다. (예: recruitment -> A1, A2 ...)
 */
public record TemplateCreateRequest(

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
