package com.van.cardnews.domain.publish.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 인프로세스 발행 워커 트리거입니다. Cloud Scheduler 잡(VAN-24)이 생기기 전까지의 대체재입니다.
 *
 * {@link PublishWorker}의 주석대로 인프로세스 주기 실행은 <b>예약 발행에는 신뢰할 수 없습니다</b> —
 * Cloud Run이 유휴 시 인스턴스를 0으로 줄이므로 아무도 요청을 보내지 않는 새벽의 예약은 뜨지 않습니다.
 * 이 클래스가 겨냥하는 것은 그 경우가 아니라 <b>사용자가 화면 앞에서 누른 즉시 발행</b>입니다.
 * 그때는 화면이 상태를 폴링하므로 인스턴스가 깨어 있고, 그래서 이 주기가 실제로 돕니다.
 *
 * 두 경로가 동시에 돌아도 중복 발행은 나지 않습니다. 선점은
 * {@code PublishRecordRepository.claim()}의 조건부 UPDATE 한 곳에서만 이뤄집니다.
 *
 * ponytail: 스케줄러 스레드 1개로 순차 실행한다. 한 건이 컨테이너 폴링에 최대 5분을 쓰므로
 * 여러 건이 밀리면 뒤 건이 그만큼 기다린다. 동시 발행이 실제로 필요해지면 그때 풀을 늘린다.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.publish.worker.in-process.enabled", havingValue = "true")
public class PublishWorkerScheduler {

    private final PublishWorker publishWorker;

    /**
     * 앞선 실행이 끝난 뒤부터 간격을 셉니다(fixedRate가 아니라 fixedDelay).
     * 한 건이 5분을 쓰는 동안 실행이 쌓이면 같은 큐를 여러 스레드가 훑게 됩니다.
     */
    @Scheduled(
            fixedDelayString = "${app.publish.worker.in-process.interval-ms}",
            initialDelayString = "${app.publish.worker.in-process.interval-ms}")
    public void runDue() {
        try {
            publishWorker.runDue();
        } catch (Exception e) {
            // 여기서 예외가 올라가면 스케줄러가 이 작업을 영구히 멈춘다. 다음 주기는 계속 와야 한다.
            log.error("인프로세스 발행 워커 실행 실패", e);
        }
    }
}
