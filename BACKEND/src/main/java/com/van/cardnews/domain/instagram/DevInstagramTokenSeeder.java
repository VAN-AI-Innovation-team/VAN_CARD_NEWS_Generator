package com.van.cardnews.domain.instagram;

import com.van.cardnews.domain.instagram.entity.InstagramToken;
import com.van.cardnews.domain.instagram.repository.InstagramTokenRepository;
import com.van.cardnews.domain.instagram.service.TokenCipher;
import com.van.cardnews.global.time.KoreaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * dev 프로필에서 더미 장기 토큰을 한 번 심습니다.
 *
 * 실 토큰은 계정 관리자의 OAuth가 있어야 나오고 그건 마지막 전환 단계(VAN-18)입니다.
 * 그때까지 암복호·저장·갱신·만료 경고를 전부 돌려볼 수 있게 하는 것이 이 시더의 목적입니다.
 *
 * 발급 시각을 이틀 전으로 두어 첫 갱신 호출이 24시간 규칙에 걸리지 않게 합니다.
 * (호출 직후 다시 호출하면 이번엔 스킵되는 것으로 24시간 규칙도 눈으로 확인할 수 있습니다.)
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevInstagramTokenSeeder implements ApplicationRunner {

    private static final String DEV_IG_USER_ID = "dev-ig-user";
    private static final String DEV_DUMMY_TOKEN = "dev-dummy-ig-long-lived-token";

    private final InstagramTokenRepository instagramTokenRepository;
    private final TokenCipher tokenCipher;

    @Override
    public void run(ApplicationArguments args) {
        if (instagramTokenRepository.count() > 0) {
            return;
        }

        LocalDateTime issuedAt = KoreaTime.now().minusDays(2);

        instagramTokenRepository.save(InstagramToken.issue(
                DEV_IG_USER_ID,
                tokenCipher.encrypt(DEV_DUMMY_TOKEN),
                issuedAt,
                issuedAt.plusDays(60)));

        log.info("dev 더미 인스타그램 토큰을 심었습니다. (ig_user_id={})", DEV_IG_USER_ID);
    }
}
