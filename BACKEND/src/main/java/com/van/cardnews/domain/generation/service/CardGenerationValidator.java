package com.van.cardnews.domain.generation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;
import org.springframework.stereotype.Component;

@Component
public class CardGenerationValidator {

    private static final int DEFAULT_MAX_CHARS = 50;

    /**
     * Claude가 반환한 최초 결과를 검증합니다.
     */
    public void validateInitial(
            CardGenerationResult result,
            JsonNode layoutDefinition
    ) {
        if (result == null) {
            throw new IllegalArgumentException(
                    "AI 카드 생성 결과가 없습니다."
            );
        }

        validateJsonInternal(
                result,
                layoutDefinition
        );
    }

    /**
     * 기존 호출부와의 호환을 위해 메서드는 유지합니다.
     *
     * 현재 구조에서는 cropArea를 필수로 검사하지 않습니다.
     */
    public void validateFinal(
            CardGenerationResult result,
            JsonNode layoutDefinition
    ) {
        if (result == null) {
            throw new IllegalArgumentException(
                    "카드 구성 결과가 없습니다."
            );
        }

        validateJsonInternal(
                result,
                layoutDefinition
        );
    }

    /**
     * ContentService 등 외부에서 카드 구성 결과를 검증할 때 사용합니다.
     *
     * cropArea는 검증하지 않습니다.
     */
    public static void validateJson(
            CardGenerationResult result,
            JsonNode layoutDefinition
    ) {
        validateJsonInternal(
                result,
                layoutDefinition
        );
    }

    private static void validateJsonInternal(
            CardGenerationResult result,
            JsonNode layoutDefinition
    ) {
        if (result == null) {
            throw new IllegalArgumentException(
                    "카드 구성 결과가 없습니다."
            );
        }

        if (result.cover() == null) {
            throw new IllegalArgumentException(
                    "표지 카드가 없습니다."
            );
        }

        if (result.content() == null ||
                result.content().isEmpty()) {

            throw new IllegalArgumentException(
                    "본문 카드가 없습니다."
            );
        }

        if (result.closing() == null) {
            throw new IllegalArgumentException(
                    "마무리 카드가 없습니다."
            );
        }

        if (layoutDefinition == null ||
                layoutDefinition.isNull()) {

            throw new IllegalArgumentException(
                    "템플릿 레이아웃이 없습니다."
            );
        }

        JsonNode cards =
                layoutDefinition.path("cards");

        if (!cards.isObject()) {
            throw new IllegalArgumentException(
                    "템플릿 cards 레이아웃이 없습니다."
            );
        }

        JsonNode coverElements =
                cards.path("cover")
                        .path("elements");

        JsonNode contentElements =
                cards.path("content")
                        .path("elements");

        validateText(
                "cover.title",
                result.cover().title(),
                getMaxChars(
                        coverElements,
                        "title"
                )
        );

        validateText(
                "cover.highlight",
                result.cover().highlight(),
                getMaxChars(
                        coverElements,
                        "highlight"
                )
        );

        int titleMax =
                getMaxChars(
                        contentElements,
                        "title"
                );

        int bodyMax =
                getMaxChars(
                        contentElements,
                        "body"
                );

        int highlightMax =
                getMaxChars(
                        contentElements,
                        "highlight"
                );

        for (int i = 0;
             i < result.content().size();
             i++) {

            CardGenerationResult.ContentCard card =
                    result.content().get(i);

            String prefix =
                    "content[" + i + "]";

            validateText(
                    prefix + ".title",
                    card.title(),
                    titleMax
            );

            validateText(
                    prefix + ".body",
                    card.body(),
                    bodyMax
            );

            validateText(
                    prefix + ".highlight",
                    card.highlight(),
                    highlightMax
            );
        }

        JsonNode closingElements =
                cards.path("closing")
                        .path("elements");

        validateText(
                "closing.cta",
                result.closing().cta(),
                getMaxCharsByContentField(
                        closingElements,
                        "cta",
                        20
                )
        );

        // 최신 템플릿은 closing에도 image 요소가 있으므로 이미지 선택을 검증합니다.
        if (hasContentFieldOrRole(closingElements, "image", "image")) {
            if (result.closing().imageId() == null) {
                throw new IllegalArgumentException(
                        "closing에 이미지가 선택되지 않았습니다."
                );
            }
        }
    }

    private static int getMaxCharsByContentField(
            JsonNode elements,
            String contentField,
            int defaultMaxChars
    ) {
        if (!elements.isObject()) {
            return defaultMaxChars;
        }

        var fields = elements.fields();

        while (fields.hasNext()) {
            var entry = fields.next();

            if (contentField.equals(
                    entry.getValue().path("contentField").asText(null)
            )) {
                JsonNode maxChars = entry.getValue().path("maxChars");

                if (maxChars.isInt()) {
                    return maxChars.asInt();
                }

                return defaultMaxChars;
            }
        }

        return defaultMaxChars;
    }

    private static boolean hasContentFieldOrRole(
            JsonNode elements,
            String contentField,
            String role
    ) {
        if (!elements.isObject()) {
            return false;
        }

        var fields = elements.fields();

        while (fields.hasNext()) {
            var element = fields.next().getValue();

            if (contentField.equals(
                    element.path("contentField").asText(null)
            ) || role.equals(
                    element.path("role").asText(null)
            )) {
                return true;
            }
        }

        return false;
    }

    private static int getMaxChars(
            JsonNode elements,
            String elementName
    ) {
        JsonNode maxChars =
                elements.path(elementName)
                        .path("maxChars");

        if (!maxChars.isInt()) {
            return DEFAULT_MAX_CHARS;
        }

        return maxChars.asInt();
    }

    private static void validateText(
            String fieldName,
            String value,
            int maxChars
    ) {
        if (value == null ||
                value.isBlank()) {

            throw new IllegalArgumentException(
                    fieldName
                            + " 값이 비어 있습니다."
            );
        }

        int length =
                value.codePointCount(
                        0,
                        value.length()
                );

        if (length > maxChars) {
            throw new IllegalArgumentException(
                    fieldName
                            + "가 최대 글자 수를 초과했습니다. "
                            + "현재="
                            + length
                            + ", 최대="
                            + maxChars
            );
        }
    }
}
