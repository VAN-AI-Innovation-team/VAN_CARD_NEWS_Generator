package com.van.cardnews.global.ai.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
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

    public ClaudeClientImpl(
            @Value("${app.ai.claude.model}") String model,
            @Value("${app.ai.claude.max-tokens}") long maxTokens
    ) {
        this.client = AnthropicOkHttpClient.fromEnv();
        this.model = model;
        this.maxTokens = maxTokens;
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

    private List<ContentBlockParam> buildContentBlocks(
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

        blocks.add(
                ContentBlockParam.ofText(
                        TextBlockParam.builder()
                                .text(buildPrompt(request))
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
                1. cover에는 표지 제목, 강조 문구, imageId, 그리고 적절한 크롭 영역(cropArea: x, y, width, height)을 구성합니다.
                2. content는 본문을 의미 단위로 나눕니다.
                3. 각 content 카드에 적절한 imageId와 크롭 영역(cropArea: x, y, width, height)을 반드시 포함합니다.
                4. 같은 이미지를 여러 카드에서 재사용할 수 있습니다.
                5. closing에는 imageId와 cropArea를 넣지 않습니다.
                6. title, body, highlight는 입력 원문에 근거해야 합니다.
                7. highlight는 날짜, 숫자, 핵심 조건, 대상, 핵심 행동 등을 우선합니다.
                8. 문장을 글자 수 기준으로 단순 절단하지 말고 문맥을 유지합니다.
                9. 템플릿의 maxChars를 반드시 준수합니다.
                """.formatted(
                request.contentType(),
                request.title(),
                request.body(),
                request.layoutDefinition()
        );
    }
}
