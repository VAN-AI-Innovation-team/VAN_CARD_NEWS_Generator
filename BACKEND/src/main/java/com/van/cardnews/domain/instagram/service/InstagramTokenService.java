package com.van.cardnews.domain.instagram.service;

import com.van.cardnews.domain.instagram.dto.response.TokenRefreshResponse;
import com.van.cardnews.domain.instagram.entity.InstagramToken;
import com.van.cardnews.domain.instagram.repository.InstagramTokenRepository;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import com.van.cardnews.global.instagram.InstagramCredentials;
import com.van.cardnews.global.instagram.InstagramTokenClient;
import com.van.cardnews.global.time.KoreaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 인스타그램 장기 토큰의 보관과 갱신을 담당합니다.
 *
 * 토큰은 DB에 암호문으로만 있고 읽을 때마다 복호화합니다.
 * 덕분에 갱신 결과가 재기동 없이 곧바로 다음 발행에 반영됩니다(configtree는 부팅 시 1회 로드라 불가능).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramTokenService {

    private final InstagramTokenRepository instagramTokenRepository;
    private final InstagramTokenClient instagramTokenClient;
    private final TokenCipher tokenCipher;

    /**
     * 발행에 쓸 현재 자격(ig_user_id + 액세스 토큰)입니다.
     * 호출할 때마다 DB에서 읽으므로 갱신 직후 값이 바로 보입니다.
     */
    @Transactional(readOnly = true)
    public InstagramCredentials current() {
        InstagramToken token = loadToken();

        if (token.isExpiredAt(KoreaTime.now())) {
            throw new CustomException(
                    ErrorCode.INSTAGRAM_TOKEN_NOT_FOUND,
                    "인스타그램 액세스 토큰이 만료되었습니다. 계정 관리자의 재인증이 필요합니다.");
        }

        return new InstagramCredentials(
                token.getIgUserId(),
                tokenCipher.decrypt(token.getAccessTokenEncrypted()));
    }

    /**
     * Cloud Scheduler가 주 1회 두드리는 갱신 로직입니다.
     * 실패하면 예외를 그대로 올려 잡의 재시도 설정이 처리하게 합니다.
     */
    @Transactional
    public TokenRefreshResponse refresh() {
        InstagramToken token = loadToken();
        LocalDateTime now = KoreaTime.now();

        if (!token.isRefreshableAt(now)) {
            log.info("인스타그램 토큰 갱신 스킵 — 발급 24시간 미경과 (갱신 가능 시각 {})", token.refreshableFrom());
            warnIfNearingExpiry(token, now);
            return TokenRefreshResponse.of(TokenRefreshResult.SKIPPED_TOO_EARLY, token, now);
        }

        try {
            InstagramTokenClient.RefreshedToken refreshed =
                    instagramTokenClient.refresh(tokenCipher.decrypt(token.getAccessTokenEncrypted()));

            token.applyRefresh(
                    tokenCipher.encrypt(refreshed.accessToken()),
                    now,
                    now.plusSeconds(refreshed.expiresInSeconds()));
        } catch (Exception e) {
            // 알림 채널이 아직 없어 error 로그가 유일한 신호다. 만료가 임박한 상태의 실패는 특히 위험하다.
            log.error("인스타그램 토큰 갱신 실패 — 만료 {} (D-{})",
                    token.getExpiresAt(), token.daysUntilExpiry(now), e);
            throw new CustomException(ErrorCode.INSTAGRAM_TOKEN_REFRESH_FAILED);
        }

        log.info("인스타그램 토큰 갱신 완료 — 새 만료 {}", token.getExpiresAt());
        warnIfNearingExpiry(token, now);

        return TokenRefreshResponse.of(TokenRefreshResult.REFRESHED, token, now);
    }

    private InstagramToken loadToken() {
        return instagramTokenRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new CustomException(ErrorCode.INSTAGRAM_TOKEN_NOT_FOUND));
    }

    /**
     * 만료 D-14부터 경고합니다. 60일 만료를 놓치면 계정 관리자가 OAuth를 다시 통과해야 합니다.
     */
    private void warnIfNearingExpiry(InstagramToken token, LocalDateTime now) {
        if (token.isNearingExpiryAt(now)) {
            log.warn("인스타그램 토큰 만료 임박 — D-{} (만료 {}). 갱신이 계속 실패하면 재인증이 필요하다.",
                    token.daysUntilExpiry(now), token.getExpiresAt());
        }
    }
}
