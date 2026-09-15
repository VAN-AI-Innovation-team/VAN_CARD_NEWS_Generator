package com.van.cardnews.domain.publish.controller;

import com.van.cardnews.domain.publish.dto.request.InstagramPublishRequest;
import com.van.cardnews.domain.publish.dto.request.InstagramScheduleRequest;
import com.van.cardnews.domain.publish.dto.request.PublishCaptionRequest;
import com.van.cardnews.domain.publish.dto.response.PublishCaptionResponse;
import com.van.cardnews.domain.publish.dto.response.PublishRecordResponse;
import com.van.cardnews.domain.publish.service.InstagramPublishService;
import com.van.cardnews.domain.publish.service.PublishWorker;
import jakarta.validation.Valid;
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
    private final PublishWorker publishWorker;

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

    /**
     * 큐에 넣은 건을 이 요청 안에서 실행합니다. 화면이 발행 요청 직후 이어서 호출합니다.
     *
     * 등록(202)과 실행을 나눈 채로 두면 실행을 깨우는 주체가 배포 환경에 있어야 하는데,
     * Cloud Scheduler 잡(VAN-24)이 아직 없고 인프로세스 스케줄러는 CPU 스로틀링 때문에
     * 요청 밖에서 신뢰할 수 없습니다. 그래서 사용자가 기다리는 발행만큼은 사용자의 요청이 끝냅니다.
     *
     * 응답을 기다리지 않아도 됩니다 — 진행 상황은 상태 조회로 봅니다.
     */
    @PostMapping("/run")
    public ResponseEntity<PublishRecordResponse> run(@PathVariable Long contentId) {
        return ResponseEntity.ok(PublishRecordResponse.from(publishWorker.runNow(contentId)));
    }

    /**
     * 발행 미리보기용 캡션 조회 — 저장된 수정본이 없으면 조립한 기본값을 준다.
     *
     * 발행 기록과 무관하므로 아직 발행을 요청하지 않은 콘텐츠도 조회된다.
     */
    @GetMapping("/caption")
    public ResponseEntity<PublishCaptionResponse> caption(@PathVariable Long contentId) {
        return ResponseEntity.ok(new PublishCaptionResponse(
                contentId, instagramPublishService.caption(contentId)));
    }

    /** 미리보기에서 고친 캡션 저장 — 이후 발행은 이 문구로 나간다. */
    @PutMapping("/caption")
    public ResponseEntity<PublishCaptionResponse> updateCaption(
            @PathVariable Long contentId,
            @Valid @RequestBody PublishCaptionRequest request
    ) {
        return ResponseEntity.ok(new PublishCaptionResponse(
                contentId, instagramPublishService.updateCaption(contentId, request.caption())));
    }

    /** 최신 발행 건의 상태 조회 — 예약·진행·성공·실패를 한 응답으로 본다. */
    @GetMapping
    public ResponseEntity<PublishRecordResponse> status(@PathVariable Long contentId) {
        return ResponseEntity.ok(PublishRecordResponse.from(instagramPublishService.latest(contentId)));
    }
}
