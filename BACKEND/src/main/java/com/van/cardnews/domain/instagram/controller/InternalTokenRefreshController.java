package com.van.cardnews.domain.instagram.controller;

import com.van.cardnews.domain.instagram.dto.response.TokenRefreshResponse;
import com.van.cardnews.domain.instagram.service.InstagramTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cloud Scheduler가 주 1회 두드리는 토큰 갱신 트리거입니다.
 *
 * Cloud Run은 유휴 시 인스턴스를 0으로 줄이므로 인프로세스 {@code @Scheduled}는 신뢰할 수 없습니다.
 * 인증은 {@code /internal/**} 공유 시크릿 헤더로 걸려 있습니다(WebConfig).
 * 재시도는 앱이 아니라 Cloud Scheduler 잡 설정이 담당하므로 실패는 그대로 5xx로 올립니다.
 */
@RestController
@RequiredArgsConstructor
public class InternalTokenRefreshController {

    private final InstagramTokenService instagramTokenService;

    @PostMapping("/internal/scheduler/refresh-token")
    public ResponseEntity<TokenRefreshResponse> refreshToken() {
        return ResponseEntity.ok(instagramTokenService.refresh());
    }
}
