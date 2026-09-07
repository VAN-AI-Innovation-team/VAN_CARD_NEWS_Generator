package com.van.cardnews.domain.publish.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;
import com.van.cardnews.domain.template.entity.Template;
import com.van.cardnews.domain.template.entity.TemplateContentType;
import com.van.cardnews.global.ai.openai.MockOpenAIClient;
import com.van.cardnews.global.ai.openai.OpenAIClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 캡션·대체 텍스트 조립 규칙 — dev와 prod 양쪽 판정에서 같은 로직이 반대로 동작해야 한다.
 *
 * dev에서는 Mock이 지어낸 고정 문구가 빠지고, prod에서는 그 자리에 실제 생성 문구가 들어오므로
 * 같은 필드가 캡션의 주재료가 된다. 후자를 검증하지 않으면 prod에서 재료를 통째로 버리는 것을 못 잡는다.
 */
class PublishTextComposerTest {

    private static final String USER_TITLE = "사용자가 입력한 제목";
    private static final String USER_BODY = "사용자가 입력한 본문";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** dev 프로필의 판정기 — 자기가 넣은 고정 문구를 안다. */
    private PublishTextComposer devComposer;

    /** prod 형태의 판정기 — 돌려준 문구가 전부 실제 생성물이라 걸러낼 자리 채움이 없다(기본값 false). */
    private PublishTextComposer prodComposer;

    private Content content;

    @BeforeEach
    void setUp() {
        devComposer = new PublishTextComposer(new MockOpenAIClient(), objectMapper);
        prodComposer = new PublishTextComposer(mock(OpenAIClient.class), objectMapper);
        content = givenContent(TemplateContentType.NEWS, mockShapedResult(USER_BODY));
    }

    /** MockOpenAIClient가 실제로 만들어내는 형태 그대로다. */
    private CardGenerationResult mockShapedResult(String body) {
        return new CardGenerationResult(
                new CardGenerationResult.CoverContent(
                        USER_TITLE, "핵심 안내", null, null, 1L, null),
                List.of(new CardGenerationResult.ContentCard(
                        "주요 내용", body, "지금 확인하세요", null, null, 2L, null)),
                new CardGenerationResult.ClosingContent("지금 확인하기", 1L, null));
    }

    private Content givenContent(TemplateContentType contentType, CardGenerationResult result) {
        Template template = mock(Template.class);
        lenient().when(template.getContentType()).thenReturn(contentType);

        Content content = mock(Content.class);
        lenient().when(content.getTemplate()).thenReturn(template);
        lenient().when(content.getTitle()).thenReturn(USER_TITLE);
        lenient().when(content.getBody()).thenReturn(USER_BODY);
        lenient().when(content.getCardGenerationResult())
                .thenReturn(result == null ? null : objectMapper.valueToTree(result));

        return content;
    }

    private GeneratedCardImage card(GeneratedCardImage.CardType cardType, int cardIndex, int sortOrder) {
        return GeneratedCardImage.create(
                content, cardType, cardIndex, sortOrder,
                "https://example.com/card-" + sortOrder + ".jpg", 1080, 1080, null);
    }

    // ------------------------------------------------------------------
    // 캡션
    // ------------------------------------------------------------------

    @Test
    void 생성기가_자리만_채운_문구는_캡션에_들어가지_않는다() {
        String caption = devComposer.caption(content);

        assertThat(caption).contains(USER_TITLE, USER_BODY);
        assertThat(caption).doesNotContain("핵심 안내", "주요 내용", "지금 확인하세요", "지금 확인하기");
    }

    /** 같은 조립 로직에 prod 형태의 입력을 주면 그 필드들이 주재료가 된다. */
    @Test
    void prod_판정에서는_같은_필드가_캡션에_포함된다() {
        String caption = prodComposer.caption(content);

        assertThat(caption).contains("핵심 안내", "주요 내용", "지금 확인하세요", "지금 확인하기");
    }

    /** 표식이 값에 걸려 있으므로 사용자가 그 필드를 고치면 판정이 저절로 풀린다. */
    @Test
    void 사용자가_고친_값은_제외되지_않는다() {
        content = givenContent(TemplateContentType.NEWS, new CardGenerationResult(
                new CardGenerationResult.CoverContent(
                        USER_TITLE, "우리 팀 소식", null, null, 1L, null),
                List.of(new CardGenerationResult.ContentCard(
                        "주요 내용", USER_BODY, "지금 확인하세요", null, null, 2L, null)),
                new CardGenerationResult.ClosingContent("지금 확인하기", 1L, null)));

        assertThat(devComposer.caption(content)).contains("우리 팀 소식");
    }

    @Test
    void 콘텐츠_유형별_해시태그가_붙는다() {
        assertThat(devComposer.caption(givenContent(TemplateContentType.NEWS, mockShapedResult(USER_BODY))))
                .contains("#소식");
        assertThat(devComposer.caption(givenContent(TemplateContentType.EVENT, mockShapedResult(USER_BODY))))
                .contains("#행사");
        assertThat(devComposer.caption(givenContent(TemplateContentType.RECRUITMENT, mockShapedResult(USER_BODY))))
                .contains("#모집");
        assertThat(devComposer.caption(givenContent(TemplateContentType.QUOTE, mockShapedResult(USER_BODY))))
                .contains("#문장");
    }

    /** 상한을 넘겨도 잘리는 것은 본문이다. 해시태그와 CTA는 잘리면 의미를 잃는다. */
    @Test
    void 캡션이_상한을_넘으면_본문만_잘라낸다() {
        content = givenContent(TemplateContentType.NEWS, mockShapedResult("본문".repeat(3000)));

        String caption = devComposer.caption(content);

        assertThat(caption.length()).isLessThanOrEqualTo(PublishPreflightValidator.MAX_CAPTION_LENGTH);
        assertThat(caption).contains("#소식", "#공지", "#뉴스", "#안내");
        assertThat(caption).contains("…");
    }

    @Test
    void 카드_구성_결과가_없으면_제목과_본문으로_만든다() {
        content = givenContent(TemplateContentType.NEWS, null);

        assertThat(devComposer.caption(content)).contains(USER_TITLE, USER_BODY, "#소식");
    }

    // ------------------------------------------------------------------
    // 대체 텍스트
    // ------------------------------------------------------------------

    @Test
    void 카드_유형별로_대체_텍스트를_만든다() {
        assertThat(devComposer.altText(content, card(GeneratedCardImage.CardType.COVER, 0, 0)))
                .isEqualTo(USER_TITLE);
        assertThat(devComposer.altText(content, card(GeneratedCardImage.CardType.CONTENT, 0, 1)))
                .isEqualTo(USER_BODY);
        assertThat(prodComposer.altText(content, card(GeneratedCardImage.CardType.CLOSING, 0, 2)))
                .isEqualTo("지금 확인하기");
    }

    /** 비어 있는 대체 텍스트는 화면 낭독기 사용자에게 그 카드가 없는 것과 같다. */
    @Test
    void 쓸_문구가_없으면_순서라도_알려준다() {
        assertThat(devComposer.altText(content, card(GeneratedCardImage.CardType.CLOSING, 0, 2)))
                .isEqualTo("카드뉴스 3번째 이미지");
    }

    @Test
    void 대체_텍스트는_상한을_넘지_않는다() {
        content = givenContent(TemplateContentType.NEWS, mockShapedResult("본문".repeat(3000)));

        assertThat(devComposer.altText(content, card(GeneratedCardImage.CardType.CONTENT, 0, 1)).length())
                .isLessThanOrEqualTo(PublishTextComposer.MAX_ALT_TEXT_LENGTH);
    }
}
