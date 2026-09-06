package com.van.cardnews.global.instagram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * {@code GET graph.instagram.com/refresh_access_token} 로 장기 토큰을 갱신합니다.
 *
 * 이 API는 토큰을 쿼리 파라미터로 받습니다. 그래서 요청 URI 자체가 비밀이고,
 * <b>URI나 원인 예외를 그대로 로그·예외에 실으면 토큰이 새어 나갑니다.</b>
 * 여기서 던지는 예외는 상태 코드와 예외 종류까지만 담고 원인 예외를 연결하지 않습니다.
 */
@Component
@Profile("prod")
public class InstagramTokenClientImpl implements InstagramTokenClient {

    private static final String REFRESH_PATH = "/refresh_access_token?grant_type=ig_refresh_token&access_token=";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public InstagramTokenClientImpl(
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
    public RefreshedToken refresh(String accessToken) {
        URI uri = URI.create(baseUrl + REFRESH_PATH
                + URLEncoder.encode(accessToken, StandardCharsets.UTF_8));

        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(30)).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("토큰 갱신 요청이 중단되었습니다.");
        } catch (Exception e) {
            // 원인 예외 메시지에 요청 URI(= 토큰)가 실릴 수 있어 종류만 남긴다
            throw new IllegalStateException(
                    "토큰 갱신 요청에 실패했습니다: " + e.getClass().getSimpleName());
        }

        if (response.statusCode() != 200) {
            throw new IllegalStateException("토큰 갱신 응답이 실패입니다. (HTTP " + response.statusCode() + ")");
        }

        try {
            JsonNode body = objectMapper.readTree(response.body());
            String refreshed = body.path("access_token").asText(null);
            long expiresIn = body.path("expires_in").asLong(0);

            if (refreshed == null || refreshed.isBlank() || expiresIn <= 0) {
                throw new IllegalStateException("토큰 갱신 응답에 access_token 또는 expires_in이 없습니다.");
            }

            return new RefreshedToken(refreshed, expiresIn);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // 응답 본문에 새 토큰이 들어 있으므로 파싱 실패 시에도 본문을 남기지 않는다
            throw new IllegalStateException(
                    "토큰 갱신 응답을 해석하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }
}
