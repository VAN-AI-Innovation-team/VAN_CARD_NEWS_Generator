package com.van.cardnews.global.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.van.cardnews.global.ai.higgsfield.HiggsfieldClientImpl;
import com.van.cardnews.global.ai.higgsfield.MockHiggsfieldClient;
import com.van.cardnews.global.ai.openai.MockOpenAIClient;
import com.van.cardnews.global.ai.openai.OpenAIClientImpl;
import com.van.cardnews.global.instagram.InstagramTokenClientImpl;
import com.van.cardnews.global.instagram.MockInstagramTokenClient;
import com.van.cardnews.global.publish.instagram.InstagramClientImpl;
import com.van.cardnews.global.publish.instagram.MockInstagramClient;
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
 * AI 두 쌍은 프로필이 아니라 {@code app.ai.mock}으로 갈린다(VAN-21). 기본값이 프로필별로
 * 잡혀 있어 프로필만 뒤집으면 결과는 종전과 같고, 그 사실을 아래 prod/dev 테스트가 그대로
 * 통과하는 것으로 고정한다. 값을 직접 준 조합은 별도 테스트가 본다.
 *
 * <b>새 prod 빈을 추가하면 이 목록에도 추가할 것</b> (예: VAN-9의 InstagramClientImpl).
 * 명시 등록이라 자동으로 따라오지 않는다.
 */
class ProdProfileClientBeanTest {

    private ApplicationContextRunner runner(String profile, String... properties) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile)
                .withPropertyValues(properties)
                .withBean(ObjectMapper.class)
                .withUserConfiguration(
                        OpenAIClientImpl.class,
                        HiggsfieldClientImpl.class,
                        InstagramTokenClientImpl.class,
                        InstagramClientImpl.class,
                        MockOpenAIClient.class,
                        MockHiggsfieldClient.class,
                        MockInstagramTokenClient.class,
                        MockInstagramClient.class,
                        AiMockInProdWarning.class
                );
    }

    @Test
    void prod_프로필에서_실구현_클라이언트가_모두_생성된다() {
        runner("prod").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OpenAIClientImpl.class);
            assertThat(context).hasSingleBean(HiggsfieldClientImpl.class);
            assertThat(context).hasSingleBean(InstagramTokenClientImpl.class);
            assertThat(context).hasSingleBean(InstagramClientImpl.class);
        });
    }

    @Test
    void prod_프로필에서는_Mock이_뜨지_않는다() {
        runner("prod").run(context -> {
            assertThat(context).doesNotHaveBean(MockOpenAIClient.class);
            assertThat(context).doesNotHaveBean(MockHiggsfieldClient.class);
            assertThat(context).doesNotHaveBean(MockInstagramTokenClient.class);
            assertThat(context).doesNotHaveBean(MockInstagramClient.class);
        });
    }

    /** 반대 방향도 고정해 둔다 — prod 조건이 항상 참이 되어 버리는 회귀를 잡는다. */
    @Test
    void dev_프로필에서는_Mock만_뜬다() {
        runner("dev").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MockOpenAIClient.class);
            assertThat(context).hasSingleBean(MockHiggsfieldClient.class);
            assertThat(context).hasSingleBean(MockInstagramTokenClient.class);
            assertThat(context).hasSingleBean(MockInstagramClient.class);
            assertThat(context).doesNotHaveBean(OpenAIClientImpl.class);
            assertThat(context).doesNotHaveBean(HiggsfieldClientImpl.class);
            assertThat(context).doesNotHaveBean(InstagramTokenClientImpl.class);
            assertThat(context).doesNotHaveBean(InstagramClientImpl.class);
        });
    }

    /**
     * 이번에 필요한 조합 — 목업 카드뉴스를 실계정에 올려 발행 경로만 검증한다.
     * AI는 실 키가 없어 실구현이면 생성 단계에서 실패하고, 키를 넣으면 검증과 무관한 비용이 든다.
     */
    @Test
    void prod_프로필에_app_ai_mock_true면_AI만_Mock이고_인스타는_실구현이다() {
        runner("prod", "app.ai.mock=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MockOpenAIClient.class);
            assertThat(context).hasSingleBean(MockHiggsfieldClient.class);
            assertThat(context).doesNotHaveBean(OpenAIClientImpl.class);
            assertThat(context).doesNotHaveBean(HiggsfieldClientImpl.class);

            assertThat(context).hasSingleBean(InstagramTokenClientImpl.class);
            assertThat(context).hasSingleBean(InstagramClientImpl.class);
            assertThat(context).doesNotHaveBean(MockInstagramTokenClient.class);
            assertThat(context).doesNotHaveBean(MockInstagramClient.class);
        });
    }

    /** 이 조합은 사고일 수도 있으므로 기동 로그에 WARN을 남기는 빈이 함께 떠야 한다. */
    @Test
    void prod_프로필에_app_ai_mock_true면_경고_빈이_뜬다() {
        runner("prod", "app.ai.mock=true").run(context ->
                assertThat(context).hasSingleBean(AiMockInProdWarning.class));

        runner("prod").run(context ->
                assertThat(context).doesNotHaveBean(AiMockInProdWarning.class));

        runner("dev").run(context ->
                assertThat(context).doesNotHaveBean(AiMockInProdWarning.class));
    }
}
