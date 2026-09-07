package com.van.cardnews.domain.publish.controller;

import com.van.cardnews.domain.publish.dto.response.PublishWorkerResponse;
import com.van.cardnews.domain.publish.service.PublishWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cloud Scheduler가 주기적으로 두드리는 발행 워커 트리거입니다.
 *
 * 인증은 {@code /internal/**} 공유 시크릿 헤더로 걸려 있습니다(WebConfig).
 * 재시도는 앱이 아니라 Cloud Scheduler 잡 설정이 담당합니다.
 */
@RestController
@RequiredArgsConstructor
public class InternalPublishWorkerController {

    private final PublishWorker publishWorker;

    @PostMapping("/internal/scheduler/publish-due")
    public ResponseEntity<PublishWorkerResponse> publishDue() {
        return ResponseEntity.ok(publishWorker.runDue());
    }
}
