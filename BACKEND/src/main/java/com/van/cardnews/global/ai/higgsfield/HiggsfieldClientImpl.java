package com.van.cardnews.global.ai.higgsfield;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.van.cardnews.global.ai.higgsfield.dto.HiggsfieldGenerationRequest;
import com.van.cardnews.global.ai.higgsfield.dto.HiggsfieldGenerationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Profile("prod")
public class HiggsfieldClientImpl implements HiggsfieldClient {

    private static final String DEFAULT_BASE_URL =
            "https://platform.higgsfield.ai";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final String apiKeyId;
    private final String apiKeySecret;
    private final String baseUrl;
    private final String model;

    private final long pollIntervalMs;
    private final int maxPollCount;

    public HiggsfieldClientImpl(
            ObjectMapper objectMapper,
            @Value("${app.ai.higgsfield.api-key-id}") String apiKeyId,
            @Value("${app.ai.higgsfield.api-key-secret}") String apiKeySecret,
            @Value("${app.ai.higgsfield.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
            @Value("${app.ai.higgsfield.model:higgsfield-ai/soul/standard}") String model,
            @Value("${app.ai.higgsfield.poll-interval-ms:2000}") long pollIntervalMs,
            @Value("${app.ai.higgsfield.max-poll-count:150}") int maxPollCount
    ) {
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .build();

        this.objectMapper = objectMapper;

        this.apiKeyId = apiKeyId;
        this.apiKeySecret = apiKeySecret;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.model = model;

        this.pollIntervalMs = pollIntervalMs;
        this.maxPollCount = maxPollCount;
    }

    @Override
    public HiggsfieldGenerationResult generateCardImages(
            HiggsfieldGenerationRequest request
    ) {
        validateCredentials();

        List<HiggsfieldGenerationResult.GeneratedCard> cards =
                new ArrayList<>();

        JsonNode cardGenerationResult =
                request.cardGenerationResult();

        /*
         * 실제 Higgsfield API도 Mock과 동일하게
         * 현재 저장된 cardGenerationResult를 기준으로
         * 카드별 이미지를 생성한다.
         */

        // COVER
        JsonNode cover =
                cardGenerationResult.path("cover");

        cards.add(
                generateCard(
                        HiggsfieldGenerationResult.GeneratedCard.CardType.COVER,
                        0,
                        cover,
                        request
                )
        );

        // CONTENT
        JsonNode contentCards =
                cardGenerationResult.path("content");

        if (contentCards.isArray()) {
            for (int i = 0; i < contentCards.size(); i++) {
                cards.add(
                        generateCard(
                                HiggsfieldGenerationResult.GeneratedCard.CardType.CONTENT,
                                i,
                                contentCards.get(i),
                                request
                        )
                );
            }
        }

        // CLOSING
        JsonNode closing =
                cardGenerationResult.path("closing");

        cards.add(
                generateCard(
                        HiggsfieldGenerationResult.GeneratedCard.CardType.CLOSING,
                        0,
                        closing,
                        request
                )
        );

        return new HiggsfieldGenerationResult(cards);
    }

    private HiggsfieldGenerationResult.GeneratedCard generateCard(
            HiggsfieldGenerationResult.GeneratedCard.CardType cardType,
            int cardIndex,
            JsonNode card,
            HiggsfieldGenerationRequest request
    ) {
        String prompt =
                buildPrompt(
                        cardType,
                        cardIndex,
                        card,
                        request
                );

        String aspectRatio =
                resolveAspectRatio(
                        request.canvasWidth(),
                        request.canvasHeight()
                );

        JsonNode submitResponse =
                submitGeneration(
                        prompt,
                        aspectRatio
                );

        String statusUrl =
                submitResponse.path("status_url").asText(null);

        String requestId =
                submitResponse.path("request_id").asText(null);

        if (statusUrl == null || statusUrl.isBlank()) {
            throw new IllegalStateException(
                    "Higgsfield 생성 요청의 status_url을 찾을 수 없습니다. " +
                            "requestId=" + requestId
            );
        }

        JsonNode result =
                pollUntilCompleted(
                        statusUrl,
                        requestId
                );

        String imageUrl =
                extractImageUrl(result);

        byte[] imageBytes =
                downloadImage(imageUrl);

        return new HiggsfieldGenerationResult.GeneratedCard(
                cardType,
                cardIndex,
                imageBytes,
                request.canvasWidth(),
                request.canvasHeight()
        );
    }

    private JsonNode submitGeneration(
            String prompt,
            String aspectRatio
    ) {
        try {
            String endpoint =
                    baseUrl + "/" + model;

            var body =
                    objectMapper.createObjectNode();

            body.put(
                    "prompt",
                    prompt
            );

            body.put(
                    "aspect_ratio",
                    aspectRatio
            );

            body.put(
                    "resolution",
                    "2K"
            );

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(endpoint))
                            .timeout(Duration.ofSeconds(60))
                            .header(
                                    "Authorization",
                                    "Key " +
                                            apiKeyId +
                                            ":" +
                                            apiKeySecret
                            )
                            .header(
                                    "Accept",
                                    "application/json"
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            body.toString()
                                    )
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                throw new IllegalStateException(
                        "Higgsfield 생성 요청 실패: HTTP " +
                                response.statusCode() +
                                " / " +
                                response.body()
                );
            }

            return objectMapper.readTree(
                    response.body()
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Higgsfield 생성 요청 중 네트워크 오류가 발생했습니다.",
                    e
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Higgsfield 생성 요청이 중단되었습니다.",
                    e
            );
        }
    }

    private JsonNode pollUntilCompleted(
            String statusUrl,
            String requestId
    ) {
        for (int i = 0; i < maxPollCount; i++) {

            JsonNode response =
                    getStatus(statusUrl);

            String status =
                    response.path("status")
                            .asText("")
                            .toLowerCase();

            log.debug(
                    "Higgsfield 이미지 생성 상태 - requestId={}, status={}, poll={}",
                    requestId,
                    status,
                    i + 1
            );

            switch (status) {
                case "completed":
                    return response;

                case "failed":
                case "nsfw":
                case "cancelled":
                case "canceled":
                    throw new IllegalStateException(
                            "Higgsfield 이미지 생성 실패: " +
                                    status +
                                    " / requestId=" +
                                    requestId +
                                    " / response=" +
                                    response
                    );

                default:
                    sleep();
            }
        }

        throw new IllegalStateException(
                "Higgsfield 이미지 생성 polling timeout: requestId=" +
                        requestId
        );
    }

    private JsonNode getStatus(
            String statusUrl
    ) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(statusUrl))
                            .timeout(Duration.ofSeconds(30))
                            .header(
                                    "Authorization",
                                    "Key " +
                                            apiKeyId +
                                            ":" +
                                            apiKeySecret
                            )
                            .header(
                                    "Accept",
                                    "application/json"
                            )
                            .GET()
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                throw new IllegalStateException(
                        "Higgsfield 상태 조회 실패: HTTP " +
                                response.statusCode() +
                                " / " +
                                response.body()
                );
            }

            return objectMapper.readTree(
                    response.body()
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Higgsfield 상태 조회 중 네트워크 오류가 발생했습니다.",
                    e
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Higgsfield 상태 조회가 중단되었습니다.",
                    e
            );
        }
    }

    private String extractImageUrl(
            JsonNode result
    ) {
        /*
         * 현재 Higgsfield SDK의 결과는
         * jobs[].results.raw.url 형태를 사용한다.
         *
         * API 응답 버전에 따라 images[].url 형태가 올 수 있어
         * 두 형태를 모두 지원한다.
         */

        JsonNode jobs =
                result.path("jobs");

        if (jobs.isArray() && !jobs.isEmpty()) {
            JsonNode firstJob =
                    jobs.get(0);

            String url =
                    firstJob
                            .path("results")
                            .path("raw")
                            .path("url")
                            .asText(null);

            if (url != null && !url.isBlank()) {
                return url;
            }

            url =
                    firstJob
                            .path("results")
                            .path("url")
                            .asText(null);

            if (url != null && !url.isBlank()) {
                return url;
            }
        }

        JsonNode images =
                result.path("images");

        if (images.isArray() && !images.isEmpty()) {
            String url =
                    images.get(0)
                            .path("url")
                            .asText(null);

            if (url != null && !url.isBlank()) {
                return url;
            }
        }

        String directUrl =
                result.path("url")
                        .asText(null);

        if (directUrl != null && !directUrl.isBlank()) {
            return directUrl;
        }

        throw new IllegalStateException(
                "Higgsfield 응답에서 생성 이미지 URL을 찾을 수 없습니다: " +
                        result
        );
    }

    private byte[] downloadImage(
            String imageUrl
    ) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(imageUrl))
                            .timeout(Duration.ofSeconds(60))
                            .GET()
                            .build();

            HttpResponse<byte[]> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofByteArray()
                    );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                throw new IllegalStateException(
                        "Higgsfield 생성 이미지 다운로드 실패: HTTP " +
                                response.statusCode()
                );
            }

            return response.body();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Higgsfield 생성 이미지 다운로드 중 오류가 발생했습니다.",
                    e
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Higgsfield 생성 이미지 다운로드가 중단되었습니다.",
                    e
            );
        }
    }

    private String buildPrompt(
            HiggsfieldGenerationResult.GeneratedCard.CardType cardType,
            int cardIndex,
            JsonNode card,
            HiggsfieldGenerationRequest request
    ) {
        StringBuilder prompt =
                new StringBuilder();

        prompt.append(
                """
                Create a polished card-news background/image for a university academic organization.

                The generated image will be used as one card in a Korean card-news design.

                Important:
                - Do not invent factual information.
                - Follow the provided card content.
                - Prioritize clean composition and readable visual hierarchy.
                - Leave appropriate visual space for text overlays.
                - Do not generate unnecessary text inside the image.
                - Match the provided layout and design direction.
                """
        );

        prompt.append("\n\n[Card Type]\n")
                .append(cardType.name());

        if (cardType ==
                HiggsfieldGenerationResult.GeneratedCard.CardType.CONTENT) {

            prompt.append("\n[Card Index]\n")
                    .append(cardIndex + 1);
        }

        prompt.append("\n\n[Card Content]\n")
                .append(
                        compactJson(card)
                );

        prompt.append("\n\n[Layout Definition]\n")
                .append(
                        compactJson(
                                request.layoutDefinition()
                        )
                );

        prompt.append("\n\n[Design Tokens]\n")
                .append(
                        compactJson(
                                request.designTokens()
                        )
                );

        return prompt.toString();
    }

    private String compactJson(
            JsonNode node
    ) {
        if (node == null || node.isMissingNode()) {
            return "{}";
        }

        return node.toString();
    }

    private String resolveAspectRatio(
            int width,
            int height
    ) {
        if (width <= 0 || height <= 0) {
            return "4:3";
        }

        double ratio =
                (double) width / height;

        if (Math.abs(ratio - 9.0 / 16.0) < 0.08) {
            return "9:16";
        }

        if (Math.abs(ratio - 16.0 / 9.0) < 0.08) {
            return "16:9";
        }

        if (Math.abs(ratio - 1.0) < 0.08) {
            return "1:1";
        }

        if (Math.abs(ratio - 4.0 / 5.0) < 0.08) {
            return "4:5";
        }

        if (Math.abs(ratio - 5.0 / 4.0) < 0.08) {
            return "5:4";
        }

        if (ratio > 1.0) {
            return "4:3";
        }

        return "3:4";
    }

    private void sleep() {
        try {
            Thread.sleep(
                    pollIntervalMs
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Higgsfield polling이 중단되었습니다.",
                    e
            );
        }
    }

    private void validateCredentials() {
        if (apiKeyId == null
                || apiKeyId.isBlank()
                || apiKeySecret == null
                || apiKeySecret.isBlank()) {

            throw new IllegalStateException(
                    "Higgsfield API credentials가 설정되지 않았습니다. " +
                            "HIGGSFIELD_API_KEY_ID와 " +
                            "HIGGSFIELD_API_KEY_SECRET을 설정해주세요."
            );
        }
    }

    private String trimTrailingSlash(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return DEFAULT_BASE_URL;
        }

        return value.endsWith("/")
                ? value.substring(
                0,
                value.length() - 1
        )
                : value;
    }
}
