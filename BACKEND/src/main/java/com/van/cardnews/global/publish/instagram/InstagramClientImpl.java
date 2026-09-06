package com.van.cardnews.global.publish.instagram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.van.cardnews.global.instagram.InstagramCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Instagram Graph API 캐러셀 발행 실구현입니다.
 *
 * HTTP는 기존 컨벤션대로 JDK {@code java.net.http.HttpClient}를 씁니다(신규 의존성 없음).
 *
 * <b>액세스 토큰이 쿼리 파라미터로 실리므로 요청 URI 자체가 비밀입니다.</b>
 * 그래서 예외에 URI·원인 예외·응답 본문을 싣지 않고 단계와 상태 코드만 남깁니다.
 */
@Component
@Profile("prod")
public class InstagramClientImpl implements InstagramClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public InstagramClientImpl(
            ObjectMapper objectMapper,
            @Value("${app.instagram.graph-base-url}") String baseUrl
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public String createCarouselItem(InstagramCredentials credentials, String imageUrl, String altText) {
        StringBuilder query = new StringBuilder()
                .append("image_url=").append(encode(imageUrl))
                .append("&is_carousel_item=true");

        if (altText != null && !altText.isBlank()) {
            query.append("&alt_text=").append(encode(altText));
        }

        return requiredText(
                post(credentials, "/" + credentials.igUserId() + "/media", query.toString(), "자식 컨테이너 생성"),
                "id",
                "자식 컨테이너 생성");
    }

    @Override
    public String createCarouselContainer(InstagramCredentials credentials, List<String> childIds, String caption) {
        if (childIds.isEmpty() || childIds.size() > MAX_CAROUSEL_ITEMS) {
            throw new IllegalArgumentException("캐러셀은 1~" + MAX_CAROUSEL_ITEMS + "장이어야 합니다.");
        }

        StringBuilder query = new StringBuilder()
                .append("media_type=CAROUSEL")
                .append("&children=").append(encode(String.join(",", childIds)));

        if (caption != null && !caption.isBlank()) {
            query.append("&caption=").append(encode(caption));
        }

        return requiredText(
                post(credentials, "/" + credentials.igUserId() + "/media", query.toString(), "캐러셀 컨테이너 생성"),
                "id",
                "캐러셀 컨테이너 생성");
    }

    @Override
    public ContainerStatus getContainerStatus(InstagramCredentials credentials, String containerId) {
        JsonNode body = get(credentials, "/" + containerId, "fields=status_code", "컨테이너 상태 조회");
        String statusCode = body.path("status_code").asText("");

        try {
            return ContainerStatus.valueOf(statusCode);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("컨테이너 상태 조회 — 알 수 없는 status_code: " + statusCode);
        }
    }

    @Override
    public String publishContainer(InstagramCredentials credentials, String containerId) {
        return requiredText(
                post(credentials,
                        "/" + credentials.igUserId() + "/media_publish",
                        "creation_id=" + encode(containerId),
                        "발행"),
                "id",
                "발행");
    }

    @Override
    public String getPermalink(InstagramCredentials credentials, String igMediaId) {
        return requiredText(
                get(credentials, "/" + igMediaId, "fields=permalink", "permalink 조회"),
                "permalink",
                "permalink 조회");
    }

    private JsonNode post(InstagramCredentials credentials, String path, String query, String step) {
        return send(
                HttpRequest.newBuilder(uri(credentials, path, query))
                        .POST(HttpRequest.BodyPublishers.noBody()),
                step);
    }

    private JsonNode get(InstagramCredentials credentials, String path, String query, String step) {
        return send(HttpRequest.newBuilder(uri(credentials, path, query)).GET(), step);
    }

    private URI uri(InstagramCredentials credentials, String path, String query) {
        return URI.create(baseUrl + path + "?" + query
                + "&access_token=" + encode(credentials.accessToken()));
    }

    private JsonNode send(HttpRequest.Builder builder, String step) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    builder.timeout(Duration.ofSeconds(30)).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(step + " 요청이 중단되었습니다.");
        } catch (Exception e) {
            // 원인 예외 메시지에 요청 URI(= 토큰)가 실릴 수 있어 종류만 남긴다
            throw new IllegalStateException(step + " 요청 실패: " + e.getClass().getSimpleName());
        }

        if (response.statusCode() != 200) {
            throw new IllegalStateException(step + " 응답 실패 (HTTP " + response.statusCode() + ")");
        }

        try {
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException(step + " 응답 해석 실패: " + e.getClass().getSimpleName());
        }
    }

    private String requiredText(JsonNode body, String field, String step) {
        String value = body.path(field).asText(null);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(step + " 응답에 " + field + "가 없습니다.");
        }

        return value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
