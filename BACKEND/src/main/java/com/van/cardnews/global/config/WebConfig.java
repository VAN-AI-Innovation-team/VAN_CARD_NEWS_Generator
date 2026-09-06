package com.van.cardnews.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.config.Customizer;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.function.Supplier;

@EnableAsync
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Cloud Scheduler가 내부 트리거 엔드포인트를 호출할 때 실어 보내는 헤더 */
    public static final String SCHEDULER_SECRET_HEADER = "X-Scheduler-Secret";

    @Value("${app.internal.scheduler-secret}")
    private String schedulerSecret;

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.generated.dir}")
    private String generatedDir;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    // 하나의 메서드 안에서 리소스 핸들러를 여러 개 등록하도록 통합
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 1. 업로드 폴더 매핑
        String uploadLocation = uploadDir.endsWith("/") ? uploadDir : uploadDir + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadLocation);

        // 2. 생성된 폴더 매핑
        String generatedLocation = generatedDir.endsWith("/") ? generatedDir : generatedDir + "/";
        registry.addResourceHandler("/generated/**")
                .addResourceLocations("file:" + generatedLocation);
    }

    // Spring Security CORS 연동 빈
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // Spring Security 필터 체인 설정
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**", "/uploads/**", "/generated/**").permitAll()
                        .requestMatchers("/internal/**").access(this::hasSchedulerSecret)
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    /**
     * 내부 트리거 엔드포인트의 공유 시크릿 검증.
     * 시크릿이 주입되지 않았으면 통과시킬 값이 없으므로 전면 거부한다(빈 문자열 우회 방지).
     */
    private AuthorizationDecision hasSchedulerSecret(Supplier<Authentication> authentication,
                                                     RequestAuthorizationContext context) {
        if (!StringUtils.hasText(schedulerSecret)) {
            return new AuthorizationDecision(false);
        }

        String provided = context.getRequest().getHeader(SCHEDULER_SECRET_HEADER);
        if (provided == null) {
            return new AuthorizationDecision(false);
        }

        // 앞자리부터 비교하다 멈추면 응답 시간 차로 값을 추측당할 수 있어 상수시간 비교를 쓴다
        return new AuthorizationDecision(MessageDigest.isEqual(
                schedulerSecret.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)));
    }
}
