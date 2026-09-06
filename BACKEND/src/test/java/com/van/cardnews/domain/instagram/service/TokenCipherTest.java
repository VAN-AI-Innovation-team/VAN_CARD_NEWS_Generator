package com.van.cardnews.domain.instagram.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCipherTest {

    private static final String TOKEN = "IGQWRPdummy-long-lived-token-value";

    private TokenCipher cipher(String key, String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new TokenCipher(key, environment);
    }

    @Test
    void 암호화한_토큰을_그대로_복호화한다() {
        TokenCipher cipher = cipher(TokenCipher.DEV_DEFAULT_KEY, "dev");

        assertThat(cipher.decrypt(cipher.encrypt(TOKEN))).isEqualTo(TOKEN);
    }

    @Test
    void 같은_토큰이라도_암호문은_매번_다르다() {
        TokenCipher cipher = cipher(TokenCipher.DEV_DEFAULT_KEY, "dev");

        // IV를 재사용하면 GCM의 안전성이 무너진다. 암호문이 같다면 IV가 고정된 것이다.
        assertThat(cipher.encrypt(TOKEN)).isNotEqualTo(cipher.encrypt(TOKEN));
    }

    @Test
    void 암호문에_평문이_남지_않는다() {
        TokenCipher cipher = cipher(TokenCipher.DEV_DEFAULT_KEY, "dev");

        assertThat(cipher.encrypt(TOKEN)).doesNotContain(TOKEN);
    }

    @Test
    void 다른_키로는_복호화되지_않는다() {
        String encrypted = cipher(TokenCipher.DEV_DEFAULT_KEY, "dev").encrypt(TOKEN);
        TokenCipher other = cipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", "dev");

        assertThatThrownBy(() -> other.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void 키가_32바이트가_아니면_기동에_실패한다() {
        assertThatThrownBy(() -> cipher("c2hvcnQta2V5", "dev"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }

    /**
     * dev 기본 키는 리포에 노출된 값이라 prod에서 쓰이면 암호화가 무의미해진다.
     * 실제 컨텍스트에서 막히는지까지 확인한다 — 프로퍼티 주입 경로를 타야 의미가 있다.
     */
    @Test
    void prod_프로필에서_dev_기본_키면_컨텍스트_기동에_실패한다() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=prod")
                .withUserConfiguration(TokenCipher.class)
                .run(context -> assertThat(context)
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .rootCause()
                        .hasMessageContaining("dev 기본 암호화 키"));
    }

    /** 반대 방향 — 키를 주입한 prod는 정상 기동한다. */
    @Test
    void prod_프로필에서_키를_주입하면_기동한다() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.instagram.token.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
                .withUserConfiguration(TokenCipher.class)
                .run(context -> assertThat(context).hasNotFailed());
    }
}
