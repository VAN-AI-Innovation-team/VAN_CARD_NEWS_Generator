package com.van.cardnews.domain.publish.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 인프로세스 발행 워커 트리거입니다. <b>보조 그물이지 주 경로가 아닙니다.</b>
 *
 * Cloud Run은 CPU 스로틀링이 켜진 기본 설정에서 <b>요청을 처리하는 동안에만</b> CPU를 줍니다.
 * 이 클래스는 요청 밖(스케줄러 스레드)에서 돌기 때문에, 배포 환경에서는 CPU를 거의 받지 못해
 * 컨테이너 폴링이 몇 분씩 걸리는 발행을 제때 끝내지 못합니다. 유휴 시 인스턴스가 0으로 줄면
 * 아예 돌지도 않습니다.
 *
 * 그래서 <b>사용자가 기다리는 발행은 {@link PublishWorker#runNow(Long)}가 요청 안에서 끝냅니다.</b>
 * 이 주기가 실제로 값을 하는 곳은 CPU 제약이 없는 로컬 개발과, 인스턴스가 마침 깨어 있을 때의
 * 재시도 회수입니다. 예약 발행의 정시 실행은 이 클래스가 아니라 Cloud Scheduler 잡(VAN-24)이
 * 담당해야 합니다.
 *
 * 세 경로(주기·요청·외부 스케줄러)가 겹쳐도 중복 발행은 나지 않습니다. 선점은
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
