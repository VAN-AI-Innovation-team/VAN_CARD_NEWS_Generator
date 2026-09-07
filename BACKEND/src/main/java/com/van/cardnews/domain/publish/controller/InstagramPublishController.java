package com.van.cardnews.domain.publish.controller;

import com.van.cardnews.domain.publish.dto.request.InstagramPublishRequest;
import com.van.cardnews.domain.publish.dto.request.InstagramScheduleRequest;
import com.van.cardnews.domain.publish.dto.response.PublishRecordResponse;
import com.van.cardnews.domain.publish.service.InstagramPublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 인스타그램 발행 요청·상태 조회 엔드포인트입니다.
 *
 * 요청은 큐에 등록만 하고 202로 답합니다. 실제 호출은 워커가 수행하므로,
 * 결과는 이 컨트롤러의 조회 엔드포인트로 확인합니다.
 */
@RestController
@RequestMapping("/api/contents/{contentId}/publish/instagram")
@RequiredArgsConstructor
public class InstagramPublishController {

    private final InstagramPublishService instagramPublishService;

    /** 발행 요청 — 승인 게이트를 통과하면 큐에 등록하고 202. */
    @PostMapping
    public ResponseEntity<PublishRecordResponse> publish(
            @PathVariable Long contentId,
            @RequestBody(required = false) InstagramPublishRequest request,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "SYSTEM") String actorId
    ) {
        String caption = request == null ? null : request.caption();

        return ResponseEntity
                .accepted()
                .body(PublishRecordResponse.from(
                        instagramPublishService.enqueue(contentId, caption, actorId)));
    }

    /** 예약 발행 등록 — 도래 시점에 워커가 집어간다. */
    @PostMapping("/schedule")
    public ResponseEntity<PublishRecordResponse> schedule(
            @PathVariable Long contentId,
            @RequestBody InstagramScheduleRequest request,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "SYSTEM") String actorId
    ) {
        return ResponseEntity
                .accepted()
                .body(PublishRecordResponse.from(instagramPublishService.schedule(
                        contentId, request.caption(), request.scheduledAt(), actorId)));
    }

    /** 예약 취소 — 워커가 이미 선점한 건은 409. */
    @DeleteMapping("/schedule")
    public ResponseEntity<PublishRecordResponse> cancelSchedule(@PathVariable Long contentId) {
        return ResponseEntity.ok(PublishRecordResponse.from(instagramPublishService.cancel(contentId)));
    }

    /** 최신 발행 건의 상태 조회 — 예약·진행·성공·실패를 한 응답으로 본다. */
    @GetMapping
    public ResponseEntity<PublishRecordResponse> status(@PathVariable Long contentId) {
        return ResponseEntity.ok(PublishRecordResponse.from(instagramPublishService.latest(contentId)));
    }
}
