package com.van.cardnews.domain.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 폼에서 전달되는 텍스트 데이터.
 * multipart/form-data 요청에서 "data" 파트(Content-Type: application/json)로 전달됩니다.
 */
public record ContentCreateRequest(

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자를 초과할 수 없습니다.")
        String title,

        @NotBlank(message = "본문은 필수입니다.")
        @Size(max = 2000, message = "본문은 2000자를 초과할 수 없습니다.")
        String body,

        @NotBlank(message = "템플릿은 필수입니다.")
        String template
) {
}
