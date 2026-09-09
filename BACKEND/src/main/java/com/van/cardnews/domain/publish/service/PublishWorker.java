package com.van.cardnews.domain.publish.service;

import com.van.cardnews.domain.publish.dto.response.PublishWorkerResponse;
import com.van.cardnews.domain.publish.entity.PublishRecord;
import com.van.cardnews.domain.publish.entity.PublishStatus;
import com.van.cardnews.domain.publish.repository.PublishRecordRepository;
import com.van.cardnews.global.time.KoreaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 도래한 발행 건을 집어 실행하는 워커입니다. Cloud Scheduler가 주기적으로 두드립니다.
 *
 * Cloud Run은 유휴 시 인스턴스를 0으로 줄이므로 인프로세스 {@code @Scheduled}로는 예약이 정시에 뜨지 않습니다.
 * 그래서 주기는 외부(Cloud Scheduler)가 만들고, 이 클래스는 "지금 도래한 것들"만 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishWorker {

    /** 아직 아무 워커도 선점하지 않은 상태. 즉시 발행(PENDING)도 같은 큐를 탄다. */
    private static final List<PublishStatus> DUE_STATUSES =
            List.of(PublishStatus.SCHEDULED, PublishStatus.PENDING);

    private static final String STUCK_MESSAGE = "발행 도중 워커가 중단되어 회수했습니다.";
    private static final String EXPIRED_MESSAGE = "예약 시각의 유예 시간을 넘겨 발행하지 않았습니다.";

    private final PublishRecordRepository publishRecordRepository;
    private final InstagramPublishService instagramPublishService;

    @Value("${app.publish.worker.batch-size}")
    private int batchSize;

    @Value("${app.publish.worker.stuck-threshold-minutes}")
    private long stuckThresholdMinutes;

    @Value("${app.publish.worker.grace-minutes}")
    private long graceMinutes;

    /**
     * 한 콘텐츠의 도래한 발행 건을 <b>지금 이 요청 안에서</b> 실행합니다.
     * 화면이 발행 버튼을 누른 직후 호출합니다.
     *
     * 배치({@link #runDue()})가 아니라 이 경로가 따로 있는 이유는 Cloud Run의 CPU 할당 때문입니다.
     * 이 서비스는 CPU 스로틀링이 켜진 기본 설정이라 <b>요청을 처리하는 동안에만</b> CPU가 나옵니다.
     * 컨테이너 폴링에 수 분이 걸리는 발행을 요청 밖(스케줄러 스레드)에서 돌리면 그 스레드는 기어갑니다.
     * 그래서 사용자가 기다리는 발행은 사용자의 요청 스레드가 직접 끝냅니다.
     *
     * 이미 끝났거나 다른 워커가 선점한 건은 건드리지 않고 현재 상태를 그대로 돌려줍니다 —
     * 화면은 이 응답이 아니라 상태 조회로 결과를 보므로, 여기서 예외를 던질 이유가 없습니다.
     *
     * ponytail: 요청 타임아웃(300초)이 컨테이너 폴링 상한(60초 × 5회)과 거의 같다. 최악의 경우
     * 요청이 먼저 끊기지만 그때도 건은 PROCESSING으로 남아 stuck 회수 경로가 집어간다.
     * 여유가 필요해지면 INSTAGRAM_POLL_INTERVAL_MS·MAX_POLL_COUNT로 줄인다.
     */
    public PublishRecord runNow(Long contentId) {
        PublishRecord record = instagramPublishService.latest(contentId);

        if (!DUE_STATUSES.contains(record.getStatus())) {
            return record;
        }

        // 아직 도래하지 않은 예약은 앞당기지 않는다. 즉시 발행도 SCHEDULED를 쓰기 때문에
        // 상태만으로는 "지금 눌린 건"과 "내일 나갈 예약"이 구분되지 않는다 — 시각이 그 구분이다.
        if (record.getScheduledAt().isAfter(KoreaTime.now())) {
            return record;
        }

        // 선점은 배치와 같은 조건부 UPDATE 한 곳을 지난다. 그래서 둘이 겹쳐도 발행은 1회다.
        if (publishRecordRepository.claim(record.getId(), KoreaTime.now()) != 1) {
            return record;
        }

        return instagramPublishService.execute(record.getId());
    }

    /**
     * 회수 → 유예 정리 → 도래 건 발행 순서로 1회 실행합니다.
     *
     * 이 메서드에 {@code @Transactional}이 없는 것은 의도입니다. 선점(claim)과 발행이 한 트랜잭션에 묶이면
     * 폴링이 끝날 때까지 행 잠금이 유지되어, 겹쳐 도는 워커가 그 뒤에서 대기합니다. 중복 발행은 그래도
     * 막히지만(대기가 풀린 뒤 조건을 재평가해 0행), 대기하는 쪽이 커넥션을 쥔 채 Cloud Run 요청
     * 타임아웃까지 가고 배치 전체가 직렬화됩니다.
     */
    public PublishWorkerResponse runDue() {
        LocalDateTime now = KoreaTime.now();

        int stuckRecovered =
                publishRecordRepository.failStuck(now.minusMinutes(stuckThresholdMinutes), STUCK_MESSAGE);
        int expired =
                publishRecordRepository.failExpired(now.minusMinutes(graceMinutes), EXPIRED_MESSAGE);

        int published = 0;
        int failed = 0;
        int skipped = 0;

        List<PublishRecord> due = publishRecordRepository
                .findByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        DUE_STATUSES, now, Limit.of(batchSize));

        for (PublishRecord record : due) {
            if (publishRecordRepository.claim(record.getId(), KoreaTime.now()) != 1) {
                skipped++;
                continue;
            }

            try {
                if (instagramPublishService.execute(record.getId()).getStatus() == PublishStatus.SUCCESS) {
                    published++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                // 한 건의 사고로 배치 전체를 멈추지 않는다. 이 건은 PROCESSING으로 남아 다음 회차가 회수한다.
                failed++;
                log.error("발행 실행 실패 — publishRecordId={}", record.getId(), e);
            }
        }

        log.info("발행 워커 — 성공 {}, 실패 {}, 선점실패 {}, 회수 {}, 유예초과 {}",
                published, failed, skipped, stuckRecovered, expired);

        return new PublishWorkerResponse(published, failed, skipped, stuckRecovered, expired);
    }
}
