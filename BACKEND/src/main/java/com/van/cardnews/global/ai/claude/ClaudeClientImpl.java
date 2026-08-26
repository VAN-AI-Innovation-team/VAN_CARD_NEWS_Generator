package com.van.cardnews.global.ai.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.van.cardnews.domain.generation.dto.request.CardGenerationRequest;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Profile("prod")
public class ClaudeClientImpl implements ClaudeClient {

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;
    private final ObjectMapper objectMapper;

    public ClaudeClientImpl(
            @Value("${app.ai.claude.model}") String model,
            @Value("${app.ai.claude.max-tokens}") long maxTokens,
            ObjectMapper objectMapper
    ) {
        this.client = AnthropicOkHttpClient.fromEnv();
        this.model = model;
        this.maxTokens = maxTokens;
        this.objectMapper = objectMapper;
    }

    @Override
    public CardGenerationResult generateCardContent(
            CardGenerationRequest request
    ) {
        List<ContentBlockParam> blocks =
                buildContentBlocks(request);

        StructuredMessageCreateParams<CardGenerationResult> params =
                StructuredMessageCreateParams
                        .<CardGenerationResult>builder()
                        .model(model)
                        .maxTokens(maxTokens)
                        .addUserMessageOfBlockParams(blocks)
                        .outputConfig(CardGenerationResult.class)
                        .build();

        StructuredMessage<CardGenerationResult> response =
                client.messages().create(params);

        CardGenerationResult result =
                response.content()
                        .stream()
                        .flatMap(contentBlock ->
                                contentBlock.text().stream()
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Claude가 카드 구성 결과를 반환하지 않았습니다."
                                )
                        )
                        .text();

        log.info(
                "Claude 카드 구성 완료 - contentType={}",
                request.contentType()
        );

        return result;
    }

    @Override
    public CardGenerationResult regenerateCardContent(
            CardGenerationRequest request,
            CardGenerationResult currentResult,
            String cardType,
            int cardIndex,
            String instruction
    ) {
        List<ContentBlockParam> blocks =
                buildContentBlocksForRegeneration(
                        request,
                        currentResult,
                        cardType,
                        cardIndex,
                        instruction
                );

        StructuredMessageCreateParams<CardGenerationResult> params =
                StructuredMessageCreateParams
                        .<CardGenerationResult>builder()
                        .model(model)
                        .maxTokens(maxTokens)
                        .addUserMessageOfBlockParams(blocks)
                        .outputConfig(CardGenerationResult.class)
                        .build();

        StructuredMessage<CardGenerationResult> response =
                client.messages().create(params);

        CardGenerationResult result =
                response.content()
                        .stream()
                        .flatMap(contentBlock ->
                                contentBlock.text().stream()
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Claude가 카드 재작성 결과를 반환하지 않았습니다."
                                )
                        )
                        .text();

        log.info(
                "Claude 카드 재작성 완료 - contentType={}, cardType={}, cardIndex={}",
                request.contentType(),
                cardType,
                cardIndex
        );

        return result;
    }

    /**
     * Claude 요청에 포함할 이미지 블록을 생성합니다.
     *
     * 일반 카드 생성과 특정 카드 재생성에서 동일한 이미지 입력을 사용하므로
     * 공통 메서드로 관리합니다.
     */
    private List<ContentBlockParam> buildImageBlocks(
            CardGenerationRequest request
    ) {
        List<ContentBlockParam> blocks =
                new ArrayList<>();

        for (CardGenerationRequest.InputImage image
                : request.images()) {

            blocks.add(
                    ContentBlockParam.ofText(
                            TextBlockParam.builder()
                                    .text(
                                            "IMAGE_ID=" +
                                                    image.imageId()
                                    )
                                    .build()
                    )
            );

            blocks.add(
                    ContentBlockParam.ofImage(
                            ImageBlockParam.builder()
                                    .source(
                                            Base64ImageSource.builder()
                                                    .mediaType(
                                                            toMediaType(
                                                                    image.mediaType()
                                                            )
                                                    )
                                                    .data(
                                                            image.base64Data()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
            );
        }

        return blocks;
    }

    private List<ContentBlockParam> buildContentBlocks(
            CardGenerationRequest request
    ) {
        List<ContentBlockParam> blocks =
                new ArrayList<>(
                        buildImageBlocks(request)
                );

        blocks.add(
                ContentBlockParam.ofText(
                        TextBlockParam.builder()
                                .text(
                                        buildPrompt(request)
                                )
                                .build()
                )
        );

        return blocks;
    }

    private List<ContentBlockParam> buildContentBlocksForRegeneration(
            CardGenerationRequest request,
            CardGenerationResult currentResult,
            String cardType,
            int cardIndex,
            String instruction
    ) {
        List<ContentBlockParam> blocks =
                new ArrayList<>(
                        buildImageBlocks(request)
                );

        blocks.add(
                ContentBlockParam.ofText(
                        TextBlockParam.builder()
                                .text(
                                        buildRegenerationPrompt(
                                                request,
                                                currentResult,
                                                cardType,
                                                cardIndex,
                                                instruction
                                        )
                                )
                                .build()
                )
        );

        return blocks;
    }

    private Base64ImageSource.MediaType toMediaType(
            String mediaType
    ) {
        return switch (mediaType) {
            case "image/jpeg" ->
                    Base64ImageSource.MediaType.IMAGE_JPEG;

            case "image/png" ->
                    Base64ImageSource.MediaType.IMAGE_PNG;

            case "image/webp" ->
                    Base64ImageSource.MediaType.IMAGE_WEBP;

            default ->
                    throw new IllegalArgumentException(
                            "지원하지 않는 이미지 형식입니다: " +
                                    mediaType
                    );
        };
    }

    private String buildPrompt(
            CardGenerationRequest request
    ) {
        return """
                당신은 대학 학회 및 학술 총연합체용 카드뉴스 콘텐츠 구성 AI입니다.

                반드시 입력된 원문에 근거해서만 작성하세요.
                원문에 없는 사실, 숫자, 일정, 인용, 혜택 등을 생성하지 마세요.

                각 이미지에는 IMAGE_ID가 있습니다.
                카드별로 가장 적합한 이미지의 IMAGE_ID를 선택하세요.

                [콘텐츠 유형]
                %s

                [제목]
                %s

                [본문 원문]
                %s

                [템플릿 레이아웃 정의]
                %s

                [작성 규칙]
                1. cover에는 표지 제목, 강조 문구, date, location, imageId, cropArea를 구성합니다.
                2. content는 본문을 의미 단위로 나눕니다.
                3. 각 content 카드에는 title, body, highlight, date, location, imageId, cropArea를 구성합니다.
                4. 같은 이미지를 여러 카드에서 재사용할 수 있습니다.
                5. closing에는 CTA와 imageId, cropArea를 구성합니다. 마무리 카드에도 입력 이미지 중 가장 적합한 이미지를 선택합니다.
                6. 템플릿의 contentField를 확인하고, date/location/cta처럼 contentField가 지정된 요소에는 해당 결과 필드를 연결할 수 있도록 값을 제공합니다.
                7. 날짜(date)는 입력 제목/본문 원문에 실제로 존재하는 날짜만 추출합니다. 원문에 없으면 null입니다.
                8. 장소(location)는 입력 제목/본문 원문에 실제로 존재하는 장소만 추출합니다. 원문에 없으면 null입니다.
                9. 날짜와 장소를 임의로 생성하거나 템플릿 JSON의 예시/placeholder를 실제 정보로 간주하지 않습니다.
                10. title, body, highlight는 입력 원문에 근거해야 합니다.
                11. highlight는 날짜, 숫자, 핵심 조건, 대상, 핵심 행동 등을 우선합니다.
                12. 문장을 글자 수 기준으로 단순 절단하지 말고 문맥을 유지합니다.
                13. 템플릿의 maxChars를 반드시 준수합니다.
                14. closing의 CTA도 입력 원문에 근거해 작성하되, 원문에서 적절한 CTA를 만들 수 없는 경우 템플릿의 기본 CTA를 사용합니다.
                15. cropArea의 x, y, width, height는 0~100 범위의 백분율 값으로 반환합니다.
                """.formatted(
                request.contentType(),
                request.title(),
                request.body(),
                request.layoutDefinition()
        );
    }

    private String buildRegenerationPrompt(
            CardGenerationRequest request,
            CardGenerationResult currentResult,
            String cardType,
            int cardIndex,
            String instruction
    ) {
        return """
                당신은 카드뉴스의 특정 카드만 수정하는 AI입니다.

                입력된 원문과 현재 카드 구성에 근거하여
                사용자가 지정한 카드 하나만 재작성하세요.

                반드시 다음 규칙을 지키세요.

                1. 지정된 카드 외의 내용은 변경하지 마세요.
                2. 지정된 카드 외의 필드는 기존 값을 그대로 유지하세요.
                3. 원문에 없는 사실, 숫자, 일정, 인용, 혜택 등을 생성하지 마세요.
                4. 날짜와 장소는 원문에 실제로 존재하는 경우에만 사용하세요.
                5. 날짜나 장소가 원문에 없다면 기존 값을 유지하거나 null로 유지하세요.
                6. 템플릿의 maxChars를 준수하세요.
                7. 카드의 의미와 기존 카드뉴스의 전체적인 문맥을 유지하세요.
                8. 사용자의 수정 요청을 우선적으로 반영하세요.
                9. 결과는 기존 CardGenerationResult 구조를 유지해야 합니다.
                10. 이미지의 imageId와 cropArea는 사용자가 직접 수정하지 않는 한 변경하지 마세요.

                [콘텐츠 유형]
                %s

                [제목]
                %s

                [본문 원문]
                %s

                [현재 카드 구성 JSON]
                %s

                [재작성 대상]
                cardType=%s
                cardIndex=%d

                [사용자 요청]
                %s
                """.formatted(
                request.contentType(),
                request.title(),
                request.body(),
                objectMapper
                        .valueToTree(currentResult)
                        .toString(),
                cardType,
                cardIndex,
                instruction
        );
    }
}
