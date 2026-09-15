package com.van.cardnews.domain.publish.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;
import com.van.cardnews.domain.template.entity.TemplateContentType;
import com.van.cardnews.global.ai.openai.OpenAIClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 발행에 나갈 캡션과 카드별 대체 텍스트를 카드 구성 결과에서 조립합니다.
 *
 * <b>어떤 값이 자리 채움인지는 이 클래스가 판정하지 않습니다.</b> {@link OpenAIClient#isPlaceholder}에 묻습니다.
 * Mock의 고정 문구를 여기서 리터럴로 걸러내면 그 규칙은 prod에서 정반대로 틀립니다 —
 * 실구현의 highlight·title·cta는 제외 대상이 아니라 캡션의 주재료입니다.
 *
 * 대체 텍스트를 저장하지 않고 매번 파생하는 것도 같은 이유의 연장입니다. 저장하면 콘텐츠가 수정될 때
 * 어긋나고, 발행 시점에 필요한 값이라 그때 만들면 항상 최신입니다.
 */
@Component
@RequiredArgsConstructor
public class PublishTextComposer {

    /** 대체 텍스트 상한. Meta 제약입니다. 캡션 상한은 {@link PublishPreflightValidator}가 들고 있습니다. */
    static final int MAX_ALT_TEXT_LENGTH = 1000;

    /**
     * 콘텐츠 유형별 기본 해시태그입니다.
     *
     * 게시 규칙 문서(VAN-14)가 확정되면 그쪽이 이 값의 출처가 됩니다. 그때까지의 기본값입니다.
     */
    private static final Map<TemplateContentType, List<String>> HASHTAGS = Map.of(
            TemplateContentType.RECRUITMENT, List.of("#모집", "#채용", "#모집공고", "#지원하기"),
            TemplateContentType.EVENT, List.of("#행사", "#이벤트", "#일정안내", "#참여하기"),
            TemplateContentType.NEWS, List.of("#소식", "#공지", "#뉴스", "#안내"),
            TemplateContentType.QUOTE, List.of("#문장", "#오늘의문장", "#기록", "#영감")
    );

    private final OpenAIClient openAIClient;
    private final ObjectMapper objectMapper;

    /**
     * 캡션을 조립합니다. 카드 구성 결과가 아직 없으면 콘텐츠의 제목·본문으로 만듭니다.
     *
     * 상한을 넘으면 <b>본문 블록만</b> 잘라냅니다. CTA와 해시태그는 길이가 정해져 있고 잘리면 의미를 잃습니다.
     */
    public String caption(Content content) {
        CardGenerationResult result = parse(content);

        List<String> body = result == null
                ? nonBlank(content.getTitle(), content.getBody())
                : bodyBlock(result);

        List<String> tail = new ArrayList<>();
        if (result != null) {
            addIfUsable(tail, "closing.cta", result.closing() == null ? null : result.closing().cta());
        }
        tail.addAll(HASHTAGS.getOrDefault(content.getTemplate().getContentType(), List.of()));

        String tailText = String.join("\n\n", tail);
        int budget = PublishPreflightValidator.MAX_CAPTION_LENGTH
                - (tailText.isEmpty() ? 0 : tailText.length() + 2);

        return join(truncate(String.join("\n\n", body), budget), tailText);
    }

    /**
     * 카드 1장의 대체 텍스트입니다. 쓸 문구가 하나도 없으면 순서만이라도 알려줍니다 —
     * 대체 텍스트가 비어 있으면 화면 낭독기 사용자에게는 그 카드가 없는 것과 같습니다.
     */
    public String altText(Content content, GeneratedCardImage card) {
        CardGenerationResult result = parse(content);
        List<String> parts = result == null ? List.of() : cardText(result, card);

        if (parts.isEmpty()) {
            return "카드뉴스 " + (card.getSortOrder() + 1) + "번째 이미지";
        }

        return truncate(String.join(" ", parts), MAX_ALT_TEXT_LENGTH);
    }

    private List<String> cardText(CardGenerationResult result, GeneratedCardImage card) {
        List<String> parts = new ArrayList<>();

        switch (card.getCardType()) {
            case COVER -> {
                if (result.cover() != null) {
                    addIfUsable(parts, "cover.title", result.cover().title());
                    addIfUsable(parts, "cover.highlight", result.cover().highlight());
                }
            }
            case CONTENT -> {
                CardGenerationResult.ContentCard content = contentCard(result, card.getCardIndex());
                if (content != null) {
                    addIfUsable(parts, "content.title", content.title());
                    addIfUsable(parts, "content.body", content.body());
                }
            }
            case CLOSING -> {
                if (result.closing() != null) {
                    addIfUsable(parts, "closing.cta", result.closing().cta());
                }
            }
        }

        return parts;
    }

    /** 표지 → 본문 카드들 → 행사 정보 순서로 쌓습니다. CTA와 해시태그는 호출부가 뒤에 붙입니다. */
    private List<String> bodyBlock(CardGenerationResult result) {
        List<String> lines = new ArrayList<>();

        if (result.cover() != null) {
            addIfUsable(lines, "cover.title", result.cover().title());
            addIfUsable(lines, "cover.highlight", result.cover().highlight());
        }

        for (CardGenerationResult.ContentCard card : nullSafe(result.content())) {
            addIfUsable(lines, "content.title", card.title());
            addIfUsable(lines, "content.body", card.body());
            addIfUsable(lines, "content.highlight", card.highlight());
        }

        if (result.cover() != null) {
            addIfUsable(lines, "cover.date", result.cover().date());
            addIfUsable(lines, "cover.location", result.cover().location());
        }

        return lines;
    }

    private CardGenerationResult.ContentCard contentCard(CardGenerationResult result, int cardIndex) {
        List<CardGenerationResult.ContentCard> cards = nullSafe(result.content());

        return cardIndex >= 0 && cardIndex < cards.size() ? cards.get(cardIndex) : null;
    }

    /** 비어 있거나 생성기가 자리만 채운 값은 쓰지 않습니다. */
    private void addIfUsable(List<String> target, String fieldPath, String value) {
        if (value == null || value.isBlank() || openAIClient.isPlaceholder(fieldPath, value.trim())) {
            return;
        }

        target.add(value.trim());
    }

    private CardGenerationResult parse(Content content) {
        if (content.getCardGenerationResult() == null) {
            return null;
        }

        return objectMapper.convertValue(content.getCardGenerationResult(), CardGenerationResult.class);
    }

    private List<String> nonBlank(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String truncate(String text, int limit) {
        if (limit <= 0) {
            return "";
        }

        return text.length() <= limit ? text : text.substring(0, limit - 1).stripTrailing() + "…";
    }

    private String join(String body, String tail) {
        if (body.isEmpty()) {
            return tail;
        }
        if (tail.isEmpty()) {
            return body;
        }

        return body + "\n\n" + tail;
    }
}
