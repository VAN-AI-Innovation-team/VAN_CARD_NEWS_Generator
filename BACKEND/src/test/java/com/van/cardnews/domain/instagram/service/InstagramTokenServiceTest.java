package com.van.cardnews.domain.instagram.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.van.cardnews.domain.instagram.dto.response.TokenRefreshResponse;
import com.van.cardnews.domain.instagram.entity.InstagramToken;
import com.van.cardnews.domain.instagram.repository.InstagramTokenRepository;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.instagram.InstagramTokenClient;
import com.van.cardnews.global.instagram.MockInstagramTokenClient;
import com.van.cardnews.global.time.KoreaTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * dev 프로필 기준 — 더미 토큰 + Mock 갱신 응답으로 저장·갱신·경고 경로를 검증한다.
 *
 * 리포지토리만 스텁이고 암복호화와 갱신 클라이언트는 실제 구현을 쓴다.
 * 목업 개발 단계에서 실제로 도는 조합이 이것이기 때문이다.
 */
class InstagramTokenServiceTest {

    private static final String DUMMY_TOKEN = "dev-dummy-ig-long-lived-token";

    private InstagramToken storedToken;
    private InstagramTokenService service;
    private TokenCipher cipher;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        cipher = new TokenCipher(TokenCipher.DEV_DEFAULT_KEY, environment);

        service = new InstagramTokenService(repositoryReturningStoredToken(), new MockInstagramTokenClient(), cipher);

        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(InstagramTokenService.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(InstagramTokenService.class)).detachAppender(logs);
    }

    /**
     * 서비스가 매번 조회로 현재 토큰을 얻는다는 점이 이 티켓의 핵심이라 조회만 스텁한다.
     * (부팅 시 1회 로드되는 configtree였다면 갱신 결과가 재기동 전까지 보이지 않는다.)
     */
    private InstagramTokenRepository repositoryReturningStoredToken() {
        InstagramTokenRepository repository = mock(InstagramTokenRepository.class);
        lenient().when(repository.findFirstByOrderByIdAsc())
                .thenAnswer(invocation -> Optional.ofNullable(storedToken));
        return repository;
    }

    private void givenToken(LocalDateTime issuedAt, LocalDateTime expiresAt) {
        storedToken = InstagramToken.issue("dev-ig-user", cipher.encrypt(DUMMY_TOKEN), issuedAt, expiresAt);
    }

    private String allLogs() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    void 갱신하면_만료일이_60일_뒤로_밀린다() {
        LocalDateTime issuedAt = KoreaTime.now().minusDays(30);
        givenToken(issuedAt, issuedAt.plusDays(60));

        TokenRefreshResponse response = service.refresh();

        assertThat(response.result()).isEqualTo(TokenRefreshResult.REFRESHED);
        assertThat(response.daysUntilExpiry()).isEqualTo(60);
        assertThat(storedToken.getExpiresAt()).isAfter(KoreaTime.now().plusDays(59));
        assertThat(storedToken.getLastRefreshedAt()).isNotNull();
    }

    @Test
    void 갱신한_토큰을_재기동_없이_바로_읽는다() {
        LocalDateTime issuedAt = KoreaTime.now().minusDays(30);
        givenToken(issuedAt, issuedAt.plusDays(60));
        assertThat(service.current().accessToken()).isEqualTo(DUMMY_TOKEN);

        service.refresh();

        assertThat(service.current().accessToken())
                .isNotEqualTo(DUMMY_TOKEN)
                .startsWith("mock-ig-long-lived-token-");
    }

    @Test
    void 발급_24시간_이내면_갱신을_시도하지_않는다() {
        LocalDateTime issuedAt = KoreaTime.now().minusHours(23);
        givenToken(issuedAt, issuedAt.plusDays(60));

        TokenRefreshResponse response = service.refresh();

        assertThat(response.result()).isEqualTo(TokenRefreshResult.SKIPPED_TOO_EARLY);
        assertThat(service.current().accessToken()).isEqualTo(DUMMY_TOKEN);
        assertThat(storedToken.getLastRefreshedAt()).isNull();
    }

    @Test
    void 발급_24시간이_지나면_갱신한다() {
        LocalDateTime issuedAt = KoreaTime.now().minusHours(25);
        givenToken(issuedAt, issuedAt.plusDays(60));

        assertThat(service.refresh().result()).isEqualTo(TokenRefreshResult.REFRESHED);
    }

    /**
     * 갱신이 계속 실패해 만료가 다가오는 상황을 잡는 유일한 신호다.
     * 스킵 경로에서도 떠야 한다 — 24시간 규칙에 걸려 스킵되는 동안에도 만료는 다가온다.
     */
    @Test
    void 만료_D14부터_경고_로그가_뜬다() {
        // 발급 1시간 전 = 24시간 규칙에 걸리는 스킵 경로. 스킵 중에도 만료는 다가온다.
        givenToken(KoreaTime.now().minusHours(1), KoreaTime.now().plusDays(13).plusHours(1));

        service.refresh();

        assertThat(allLogs()).contains("만료 임박", "D-13");
    }

    @Test
    void 만료가_멀면_경고하지_않는다() {
        LocalDateTime issuedAt = KoreaTime.now().minusDays(2);
        givenToken(issuedAt, issuedAt.plusDays(60));

        service.refresh();

        assertThat(allLogs()).doesNotContain("만료 임박");
    }

    /**
     * 실 토큰이 들어오기 전에 닫아두어야 하는 문제라 더미 토큰으로 미리 검증한다.
     * 갱신 전 토큰(더미)도, 갱신 후 새 토큰도 로그에 나오면 안 된다.
     */
    @Test
    void 토큰_평문이_로그에_남지_않는다() {
        LocalDateTime issuedAt = KoreaTime.now().minusDays(30);
        givenToken(issuedAt, issuedAt.plusDays(60));

        service.refresh();
        String newToken = service.current().accessToken();

        assertThat(allLogs()).doesNotContain(DUMMY_TOKEN).doesNotContain(newToken);
        assertThat(storedToken.toString()).doesNotContain(newToken);
    }

    @Test
    void 저장된_토큰이_없으면_예외를_던진다() {
        storedToken = null;

        assertThatThrownBy(() -> service.refresh()).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.current()).isInstanceOf(CustomException.class);
    }

    @Test
    void 만료된_토큰은_발행에_쓰지_않는다() {
        givenToken(KoreaTime.now().minusDays(61), KoreaTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.current())
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("재인증");
    }

    @Test
    void 갱신_실패는_예외로_올려_스케줄러가_재시도하게_한다() {
        LocalDateTime issuedAt = KoreaTime.now().minusDays(30);
        givenToken(issuedAt, issuedAt.plusDays(60));

        InstagramTokenClient failing = accessToken -> {
            throw new IllegalStateException("토큰 갱신 응답이 실패입니다. (HTTP 400)");
        };
        InstagramTokenService failingService =
                new InstagramTokenService(repositoryReturningStoredToken(), failing, cipher);

        assertThatThrownBy(failingService::refresh).isInstanceOf(CustomException.class);
        assertThat(storedToken.getLastRefreshedAt()).isNull();
    }
}
