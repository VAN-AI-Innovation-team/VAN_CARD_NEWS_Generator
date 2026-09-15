package com.van.cardnews.domain.publish.dto.response;

/**
 * 발행에 나갈 캡션입니다.
 *
 * 사용자가 고친 값이 있으면 그것을, 없으면 조립한 기본값을 담습니다.
 * 화면은 둘을 구분할 필요가 없습니다 — 어느 쪽이든 지금 발행하면 나갈 문구라는 점이 같습니다.
 */
public record PublishCaptionResponse(Long contentId, String caption) {
}
