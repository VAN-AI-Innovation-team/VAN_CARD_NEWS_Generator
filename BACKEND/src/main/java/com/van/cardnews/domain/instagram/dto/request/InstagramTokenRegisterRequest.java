package com.van.cardnews.domain.instagram.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 수동 OAuth로 받아온 장기 토큰을 시스템에 넣기 위한 요청입니다.
 *
 * {@code expiresInSeconds}는 Meta 응답의 값을 그대로 넣습니다. 만료 시각을 직접 계산해
 * 보내게 하면 시간대 해석이 호출자마다 갈리므로, 서버가 등록 시점 기준으로 환산합니다.
 */
public record InstagramTokenRegisterRequest(
        @NotBlank(message = "ig_user_id는 필수입니다.")
        String igUserId,

        @NotBlank(message = "액세스 토큰은 필수입니다.")
        String accessToken,

        @NotNull(message = "만료까지 남은 초는 필수입니다.")
        @Positive(message = "만료까지 남은 초는 양수여야 합니다.")
        Long expiresInSeconds
) {}
