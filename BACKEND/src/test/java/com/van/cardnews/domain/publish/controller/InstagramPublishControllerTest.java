package com.van.cardnews.domain.publish.controller;

import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.publish.entity.PublishRecord;
import com.van.cardnews.domain.publish.service.InstagramPublishService;
import com.van.cardnews.global.config.WebConfig;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import com.van.cardnews.global.exception.GlobalExceptionHandler;
import com.van.cardnews.global.time.KoreaTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 발행 엔드포인트의 응답 계약 검증 — 202/403/409/404가 그대로 나가는지 본다.
 *
 * 상태 코드는 서비스가 던지는 ErrorCode에 달려 있어 서비스 테스트만으로는 확인되지 않는다.
 */
@WebMvcTest(InstagramPublishController.class)
@Import({WebConfig.class, GlobalExceptionHandler.class})
class InstagramPublishControllerTest {

    private static final String PATH = "/api/contents/42/publish/instagram";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InstagramPublishService instagramPublishService;

    private PublishRecord record(String igMediaId, String permalink) {
        Content content = mock(Content.class);
        when(content.getId()).thenReturn(42L);

        PublishRecord record = PublishRecord.schedule(content, "INSTAGRAM", "캡션", KoreaTime.now());
        if (igMediaId != null) {
            record.markProcessing();
            record.markSuccess(igMediaId, permalink);
        }
        return record;
    }

    @Test
    void 발행_요청은_큐에_등록하고_202를_반환한다() throws Exception {
        // record()가 내부에서 다른 목을 스터빙하므로 when(...) 인자 안에서 만들면 스터빙이 겹친다
        PublishRecord scheduled = record(null, null);
        when(instagramPublishService.enqueue(anyLong(), any(), any())).thenReturn(scheduled);

        mockMvc.perform(post(PATH))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.scheduledAt").isNotEmpty());
    }

    @Test
    void 발행_요청은_X_Actor_Id를_행위자로_넘긴다() throws Exception {
        PublishRecord scheduled = record(null, null);
        when(instagramPublishService.enqueue(anyLong(), any(), any())).thenReturn(scheduled);

        mockMvc.perform(post(PATH).header("X-Actor-Id", "hanms"))
                .andExpect(status().isAccepted());

        verify(instagramPublishService).enqueue(42L, null, "hanms");
    }

    @Test
    void X_Actor_Id가_없으면_SYSTEM으로_넘긴다() throws Exception {
        PublishRecord scheduled = record(null, null);
        when(instagramPublishService.enqueue(anyLong(), any(), any())).thenReturn(scheduled);

        mockMvc.perform(post(PATH)).andExpect(status().isAccepted());

        verify(instagramPublishService).enqueue(42L, null, "SYSTEM");
    }

    @Test
    void 미승인_콘텐츠는_403과_발행_전용_메시지로_거절된다() throws Exception {
        when(instagramPublishService.enqueue(anyLong(), any(), any()))
                .thenThrow(new CustomException(ErrorCode.CONTENT_NOT_APPROVED_FOR_PUBLISH));

        mockMvc.perform(post(PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTENT_NOT_APPROVED_FOR_PUBLISH"))
                .andExpect(jsonPath("$.message").value("승인 완료된 콘텐츠만 발행할 수 있습니다."));
    }

    @Test
    void 이미_발행된_콘텐츠는_409로_거절된다() throws Exception {
        when(instagramPublishService.enqueue(anyLong(), any(), any()))
                .thenThrow(new CustomException(ErrorCode.CONTENT_ALREADY_PUBLISHED));

        mockMvc.perform(post(PATH)).andExpect(status().isConflict());
    }

    @Test
    void 예약_등록은_202와_예약_시각을_돌려준다() throws Exception {
        PublishRecord scheduled = record(null, null);
        when(instagramPublishService.schedule(anyLong(), any(), any(), any())).thenReturn(scheduled);

        mockMvc.perform(post(PATH + "/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2099-01-01T09:00:00\",\"caption\":\"캡션\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.scheduledAt").isNotEmpty());
    }

    @Test
    void 잘못된_예약_시각은_400으로_거절된다() throws Exception {
        when(instagramPublishService.schedule(anyLong(), any(), any(), any()))
                .thenThrow(new CustomException(ErrorCode.INVALID_SCHEDULE_TIME));

        mockMvc.perform(post(PATH + "/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2000-01-01T09:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHEDULE_TIME"));
    }

    @Test
    void 예약_취소는_CANCELED_상태를_돌려준다() throws Exception {
        PublishRecord canceled = record(null, null);
        canceled.cancel();
        when(instagramPublishService.cancel(anyLong())).thenReturn(canceled);

        mockMvc.perform(delete(PATH + "/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void 선점된_건의_취소는_409로_거절된다() throws Exception {
        doThrow(new CustomException(ErrorCode.PUBLISH_ALREADY_PROCESSING))
                .when(instagramPublishService).cancel(anyLong());

        mockMvc.perform(delete(PATH + "/schedule"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PUBLISH_ALREADY_PROCESSING"));
    }

    @Test
    void 상태_조회는_permalink까지_돌려준다() throws Exception {
        PublishRecord published = record("mock-media-1", "https://www.instagram.com/p/mock-media-1/");
        when(instagramPublishService.latest(anyLong())).thenReturn(published);

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.igMediaId").value("mock-media-1"))
                .andExpect(jsonPath("$.permalink").value("https://www.instagram.com/p/mock-media-1/"));
    }

    @Test
    void 발행_이력이_없으면_404를_반환한다() throws Exception {
        when(instagramPublishService.latest(anyLong()))
                .thenThrow(new CustomException(ErrorCode.PUBLISH_RECORD_NOT_FOUND));

        mockMvc.perform(get(PATH)).andExpect(status().isNotFound());
    }

    @Test
    void 캡션_조회는_발행_기록이_없어도_문구를_돌려준다() throws Exception {
        when(instagramPublishService.caption(42L)).thenReturn("조립된 캡션");

        mockMvc.perform(get(PATH + "/caption"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption").value("조립된 캡션"));
    }

    @Test
    void 캡션_저장은_저장된_문구를_돌려준다() throws Exception {
        when(instagramPublishService.updateCaption(anyLong(), any())).thenReturn("고친 캡션");

        mockMvc.perform(put(PATH + "/caption")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caption\":\"고친 캡션\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption").value("고친 캡션"));

        verify(instagramPublishService).updateCaption(42L, "고친 캡션");
    }

    @Test
    void 빈_캡션_저장은_400이다() throws Exception {
        mockMvc.perform(put(PATH + "/caption")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caption\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
