package com.van.cardnews.global.instagram;

/**
 * 인스타그램 장기 토큰 갱신 API 호출자입니다.
 *
 * dev에서는 {@link MockInstagramTokenClient}가, prod에서는 {@link InstagramTokenClientImpl}가 뜹니다.
 */
public interface InstagramTokenClient {

    /**
     * 장기 토큰을 갱신하고 새 토큰과 남은 수명을 돌려줍니다.
     *
     * @param accessToken 현재 유효한 장기 토큰(평문)
     */
    RefreshedToken refresh(String accessToken);

    record RefreshedToken(String accessToken, long expiresInSeconds) {
    }
}
