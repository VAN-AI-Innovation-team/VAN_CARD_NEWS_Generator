package com.van.cardnews.global.instagram;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * dev 프로필용 갱신 목업입니다.
 *
 * 실 토큰 없이 암복호·저장·24시간 규칙·만료 경고까지 전 경로를 돌리기 위한 것으로,
 * 호출할 때마다 새 더미 토큰과 +60일 만료를 돌려줍니다.
 */
@Component
@Profile("dev")
public class MockInstagramTokenClient implements InstagramTokenClient {

    /** Meta가 장기 토큰에 부여하는 수명과 같습니다. */
    public static final Duration MOCK_TOKEN_LIFETIME = Duration.ofDays(60);

    private final AtomicLong sequence = new AtomicLong();

    @Override
    public RefreshedToken refresh(String accessToken) {
        return new RefreshedToken(
                "mock-ig-long-lived-token-" + sequence.incrementAndGet(),
                MOCK_TOKEN_LIFETIME.toSeconds());
    }
}
