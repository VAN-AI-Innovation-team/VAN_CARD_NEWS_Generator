package com.van.cardnews.domain.instagram.dto.response;

import com.van.cardnews.domain.instagram.entity.InstagramToken;
import com.van.cardnews.domain.instagram.service.TokenRefreshResult;

import java.time.LocalDateTime;

/**
 * 갱신 트리거 응답입니다. 토큰 값은 담지 않습니다.
 */
public record TokenRefreshResponse(
        TokenRefreshResult result,
        LocalDateTime expiresAt,
        long daysUntilExpiry
) {

    public static TokenRefreshResponse of(TokenRefreshResult result, InstagramToken token, LocalDateTime now) {
        return new TokenRefreshResponse(result, token.getExpiresAt(), token.daysUntilExpiry(now));
    }
}
