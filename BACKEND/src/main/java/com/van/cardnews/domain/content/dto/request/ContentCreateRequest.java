package com.van.cardnews.domain.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 카드뉴스 생성 요청 텍스트 데이터.
 */
public record ContentCreateRequest(

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자를 초과할 수 없습니다.")
        String title,

        @NotBlank(message = "본문은 필수입니다.")
        @Size(max = 2000, message = "본문은 2000자를 초과할 수 없습니다.")
        String body,

        @NotNull(message = "템플릿은 필수입니다.")
        @Positive(message = "템플릿 ID는 0보다 커야 합니다.")
        Long templateId
) {
}
