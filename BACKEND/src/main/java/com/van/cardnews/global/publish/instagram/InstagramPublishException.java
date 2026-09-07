package com.van.cardnews.global.publish.instagram;

import lombok.Getter;

/**
 * 원인 분류를 실어 나르는 발행 실패입니다.
 *
 * 메시지는 로그용(단계·HTTP 코드)이고, 사용자에게 보이는 문구는 {@link PublishFailure#getUserMessage()}입니다.
 * 액세스 토큰이 요청 URI에 실리므로 이 예외에도 URI·원인 예외·응답 본문을 담지 않습니다.
 */
@Getter
public class InstagramPublishException extends RuntimeException {

    private final PublishFailure failure;

    public InstagramPublishException(PublishFailure failure, String message) {
        super(message);
        this.failure = failure;
    }
}
