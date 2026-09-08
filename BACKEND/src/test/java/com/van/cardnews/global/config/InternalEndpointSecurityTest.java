package com.van.cardnews.global.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cloud Scheduler가 두드릴 /internal/** 의 공유 시크릿 인증 검증.
 * 실제 트리거 엔드포인트는 아직 없으므로 스텁 컨트롤러로 필터체인을 태운다.
 *
 * /internal 아래에 scheduler 말고 다른 갈래도 생겼으므로(토큰 등록, VAN-22) 그쪽 경로도
 * 함께 태운다. 매처가 /internal/scheduler/** 로 좁아지면 새 갈래가 무인증으로 열린다.
 */
@WebMvcTest(controllers = InternalEndpointSecurityTest.StubInternalController.class)
@Import({WebConfig.class, InternalEndpointSecurityTest.StubInternalController.class})
@TestPropertySource(properties = "app.internal.scheduler-secret=" + InternalEndpointSecurityTest.SECRET)
class InternalEndpointSecurityTest {

    static final String SECRET = "test-secret-0123456789";
    private static final String PATH = "/internal/scheduler/test-trigger";
    private static final String NON_SCHEDULER_PATH = "/internal/instagram/test-trigger";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 시크릿_헤더가_없으면_거부된다() throws Exception {
        mockMvc.perform(post(PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    void 시크릿이_틀리면_거부된다() throws Exception {
        mockMvc.perform(post(PATH).header(WebConfig.SCHEDULER_SECRET_HEADER, SECRET + "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 시크릿이_맞으면_통과한다() throws Exception {
        mockMvc.perform(post(PATH).header(WebConfig.SCHEDULER_SECRET_HEADER, SECRET))
                .andExpect(status().isOk());
    }

    /** scheduler 갈래 밖의 /internal 경로도 같은 시크릿에 걸린다. */
    @Test
    void scheduler가_아닌_internal_경로도_보호된다() throws Exception {
        mockMvc.perform(post(NON_SCHEDULER_PATH))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(NON_SCHEDULER_PATH).header(WebConfig.SCHEDULER_SECRET_HEADER, SECRET))
                .andExpect(status().isOk());
    }

    @Nested
    @TestPropertySource(properties = "app.internal.scheduler-secret=")
    class 시크릿이_주입되지_않은_경우 {

        @Autowired
        private MockMvc mockMvc;

        // 시크릿이 실제로 비워졌는지까지 함께 검증하려고 일부러 '맞는' 시크릿을 보낸다.
        // 프로퍼티 오버라이드가 먹지 않았다면 200이 되어 이 테스트가 깨진다.
        @Test
        void 어떤_헤더로도_통과하지_못한다() throws Exception {
            mockMvc.perform(post(PATH).header(WebConfig.SCHEDULER_SECRET_HEADER, SECRET))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post(PATH).header(WebConfig.SCHEDULER_SECRET_HEADER, ""))
                    .andExpect(status().isForbidden());
        }
    }

    @RestController
    static class StubInternalController {

        @PostMapping(PATH)
        void trigger() {
        }

        @PostMapping(NON_SCHEDULER_PATH)
        void nonSchedulerTrigger() {
        }
    }
}
