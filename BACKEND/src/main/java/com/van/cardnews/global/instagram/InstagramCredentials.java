package com.van.cardnews.global.instagram;

/**
 * 발행 호출 한 번에 필요한 인스타그램 자격입니다.
 *
 * 액세스 토큰이 담기므로 {@code toString()}을 가려 둡니다.
 * 레코드의 기본 toString은 모든 필드를 그대로 찍기 때문에, 이 객체가 로그·예외 메시지에
 * 한 번만 실려도 토큰이 새어 나간다.
 */
public record InstagramCredentials(String igUserId, String accessToken) {

    @Override
    public String toString() {
        return "InstagramCredentials(igUserId=" + igUserId + ", accessToken=***)";
    }
}
