package com.van.cardnews.global.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> healthCheck() {
        // 프론트엔드로 보낼 응답 데이터
        return Map.of("status", "ok", "message", "Spring-React 통신 성공!");
    }
}
