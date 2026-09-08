package com.van.cardnews.global.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@code prod} 프로필인데 AI가 Mock인 조합을 기동 로그에 남긴다.
 *
 * 이 조합은 의도된 것일 수도 있고(목업 카드로 실계정 발행 경로만 검증) 사고일 수도 있다
 * (prod 전환 때 {@code AI_MOCK=true}를 끄는 것을 잊음). 후자는 <b>실패하지 않기 때문에</b>
 * 아무도 모른 채 Mock 카드가 실계정에 올라간다. 둘을 구분할 수 있는 신호는 이 로그뿐이다.
 */
@Slf4j
@Component
@Profile("prod")
@ConditionalOnProperty(name = "app.ai.mock", havingValue = "true")
public class AiMockInProdWarning {

    public AiMockInProdWarning() {
        log.warn("""
                ============================================================
                prod 프로필인데 app.ai.mock=true 다. OpenAI·Higgsfield는 Mock으로 뜬다.
                카드 내용과 이미지는 실제 생성물이 아니다. 발행 경로 검증이 목적이 아니라면
                AI_MOCK 환경변수를 끄고 다시 기동할 것.
                ============================================================""");
    }
}
