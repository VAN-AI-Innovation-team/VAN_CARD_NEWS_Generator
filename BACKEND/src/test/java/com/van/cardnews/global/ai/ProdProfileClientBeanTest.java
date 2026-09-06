package com.van.cardnews.global.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.van.cardnews.global.ai.higgsfield.HiggsfieldClientImpl;
import com.van.cardnews.global.ai.higgsfield.MockHiggsfieldClient;
import com.van.cardnews.global.ai.openai.MockOpenAIClient;
import com.van.cardnews.global.ai.openai.OpenAIClientImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @Profile("prod")} 빈이 실제로 생성되는지 검증한다.
 *
 * 개발은 목업으로 하지만 마지막에 프로필만 뒤집으면 바로 동작해야 한다. 그런데 CI도
 * dev 프로필로 돌기 때문에 prod 빈은 여기 오기 전까지 <b>한 번도 생성된 적이 없었다.</b>
 * 실제로 {@code GcsImageStorageService}가 생성자 둘에 {@code @Autowired}를 빠뜨려
 * 구현·테스트·CI·리뷰·머지를 전부 통과하고도 빈 생성에 실패한 적이 있다.
 *
 * DB는 타지 않는다. 이 테스트가 보는 것은 빈 생성이지 마이그레이션이 아니고,
 * DB를 물리면 DATABASE_URL이 있는 환경에서만 도는 테스트가 되어 로컬에서 무력해진다.
 * 실제 {@code application.properties}는 로드하므로 @Value 키 누락은 그대로 잡힌다.
 *
 * <b>새 prod 빈을 추가하면 이 목록에도 추가할 것</b> (예: VAN-9의 InstagramClientImpl).
 * 명시 등록이라 자동으로 따라오지 않는다.
 */
class ProdProfileClientBeanTest {

    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile)
                .withBean(ObjectMapper.class)
                .withUserConfiguration(
                        OpenAIClientImpl.class,
                        HiggsfieldClientImpl.class,
                        MockOpenAIClient.class,
                        MockHiggsfieldClient.class
                );
    }

    @Test
    void prod_프로필에서_실구현_클라이언트가_모두_생성된다() {
        runner("prod").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OpenAIClientImpl.class);
            assertThat(context).hasSingleBean(HiggsfieldClientImpl.class);
        });
    }

    @Test
    void prod_프로필에서는_Mock이_뜨지_않는다() {
        runner("prod").run(context -> {
            assertThat(context).doesNotHaveBean(MockOpenAIClient.class);
            assertThat(context).doesNotHaveBean(MockHiggsfieldClient.class);
        });
    }

    /** 반대 방향도 고정해 둔다 — prod 조건이 항상 참이 되어 버리는 회귀를 잡는다. */
    @Test
    void dev_프로필에서는_Mock만_뜬다() {
        runner("dev").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MockOpenAIClient.class);
            assertThat(context).hasSingleBean(MockHiggsfieldClient.class);
            assertThat(context).doesNotHaveBean(OpenAIClientImpl.class);
            assertThat(context).doesNotHaveBean(HiggsfieldClientImpl.class);
        });
    }
}
