package com.van.cardnews.domain.publish.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 미리보기에서 고친 캡션 저장 요청입니다.
 *
 * 빈 문구를 저장하면 "고친 적 없음"과 구분되지 않으므로 받지 않습니다.
 * 기본값으로 되돌리려면 DELETE를 씁니다.
 */
public record PublishCaptionRequest(@NotBlank String caption) {
}
