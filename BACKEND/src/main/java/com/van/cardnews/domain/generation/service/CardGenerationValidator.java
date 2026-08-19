package com.van.cardnews.domain.generation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CardGenerationValidator {

    private final ObjectMapper objectMapper;

    /**
     * Claude가 반환한 최초 결과를 검증합니다.
     *
     * 이 시점에서는 cropArea가 아직 계산되지 않았으므로
     * 텍스트 / 카드 구조 / imageId만 검증합니다.
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

        validateJson(
                objectMapper.valueToTree(result),
                layoutDefinition,
                false
        );
    }

    /**
     * cropArea까지 포함된 최종 카드 구성 결과를 검증합니다.
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

        validateJson(
                objectMapper.valueToTree(result),
                layoutDefinition,
                true
        );
    }

    /**
     * 수정된 카드 구성 결과를 검증합니다.
     *
     * 수정 결과는 이미 cropArea가 포함된 결과를 저장하므로
     * cropArea까지 검증합니다.
     */
    public static void validateJson(
            JsonNode result,
            JsonNode layoutDefinition
    ) {
        validateJson(
                result,
                layoutDefinition,
                true
        );
    }

    private static void validateJson(
            JsonNode result,
            JsonNode layoutDefinition,
            boolean validateCrop
    ) {
        if (result == null || result.isNull()) {
            throw new IllegalArgumentException(
                    "카드 구성 결과가 없습니다."
            );
        }

        JsonNode cover =
                result.path("cover");

        JsonNode content =
                result.path("content");

        JsonNode closing =
                result.path("closing");

        if (!cover.isObject()) {
            throw new IllegalArgumentException(
                    "표지 카드가 없습니다."
            );
        }

        if (!content.isArray() ||
                content.isEmpty()) {

            throw new IllegalArgumentException(
                    "본문 카드가 없습니다."
            );
        }

        if (!closing.isObject()) {
            throw new IllegalArgumentException(
                    "마무리 카드가 없습니다."
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
                cover.path("title")
                        .asText(null),
                getMaxChars(
                        coverElements,
                        "title"
                )
        );

        validateText(
                "cover.highlight",
                cover.path("highlight")
                        .asText(null),
                getMaxChars(
                        coverElements,
                        "highlight"
                )
        );

        if (validateCrop) {
            validateCropArea(
                    "cover.cropArea",
                    cover.path("cropArea")
            );
        }

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
             i < content.size();
             i++) {

            JsonNode card =
                    content.get(i);

            String prefix =
                    "content[" + i + "]";

            validateText(
                    prefix + ".title",
                    card.path("title")
                            .asText(null),
                    titleMax
            );

            validateText(
                    prefix + ".body",
                    card.path("body")
                            .asText(null),
                    bodyMax
            );

            validateText(
                    prefix + ".highlight",
                    card.path("highlight")
                            .asText(null),
                    highlightMax
            );

            if (validateCrop) {
                validateCropArea(
                        prefix + ".cropArea",
                        card.path("cropArea")
                );
            }
        }

        validateText(
                "closing.cta",
                closing.path("cta")
                        .asText(null),
                20
        );
    }

    private static int getMaxChars(
            JsonNode elements,
            String elementName
    ) {
        JsonNode maxChars =
                elements.path(elementName)
                        .path("maxChars");

        if (!maxChars.isInt()) {
            throw new IllegalArgumentException(
                    "템플릿의 "
                            + elementName
                            + " maxChars가 정의되지 않았습니다."
            );
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

    private static void validateCropArea(
            String fieldName,
            JsonNode cropArea
    ) {
        if (!cropArea.isObject()) {
            throw new IllegalArgumentException(
                    fieldName
                            + "가 없습니다."
            );
        }

        double x =
                cropArea.path("x")
                        .asDouble(-1);

        double y =
                cropArea.path("y")
                        .asDouble(-1);

        double width =
                cropArea.path("width")
                        .asDouble(-1);

        double height =
                cropArea.path("height")
                        .asDouble(-1);

        if (x < 0 ||
                y < 0 ||
                width <= 0 ||
                height <= 0) {

            throw new IllegalArgumentException(
                    fieldName
                            + "의 값이 올바르지 않습니다."
            );
        }
    }
}
