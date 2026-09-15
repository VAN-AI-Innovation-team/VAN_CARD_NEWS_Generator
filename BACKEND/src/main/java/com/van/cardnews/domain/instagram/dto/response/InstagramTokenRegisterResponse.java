package com.van.cardnews.domain.instagram.dto.response;

import com.van.cardnews.domain.instagram.entity.InstagramToken;

import java.time.LocalDateTime;

/**
 * 등록 결과입니다. 토큰 값은 담지 않습니다 — 등록한 쪽은 이미 값을 알고 있고,
 * 응답에 실으면 프록시·접근로그를 타고 남습니다.
 */
public record InstagramTokenRegisterResponse(
        String igUserId,
        LocalDateTime expiresAt,
        long daysUntilExpiry
) {

    public static InstagramTokenRegisterResponse of(InstagramToken token, LocalDateTime now) {
        return new InstagramTokenRegisterResponse(
                token.getIgUserId(), token.getExpiresAt(), token.daysUntilExpiry(now));
    }
}
