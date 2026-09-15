package com.van.cardnews.domain.publish.repository;

import com.van.cardnews.domain.publish.entity.PublishRecord;
import com.van.cardnews.domain.publish.entity.PublishStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PublishRecordRepository
        extends JpaRepository<PublishRecord, Long> {

    List<PublishRecord> findByContentIdOrderByIdDesc(Long contentId);

    /** 해당 채널의 최신 발행 건. 없는 것은 정상이므로 Optional로 준다. */
    Optional<PublishRecord> findTopByContentIdAndChannelOrderByIdDesc(Long contentId, String channel);

    /**
     * 워커가 실행할 대상을 가져옵니다.
     * idx_publish_records_status_scheduled_at 인덱스를 타는 조회 경로입니다.
     *
     * 건수 상한은 Cloud Run 요청 타임아웃 대비입니다. 한 건이 최대 5분(폴링 상한)을 쓰므로
     * 상한 없이 긁으면 워커 한 번이 타임아웃 안에 끝나지 않습니다.
     */
    List<PublishRecord> findByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            List<PublishStatus> statuses,
            LocalDateTime scheduledAt,
            Limit limit
    );

    /**
     * 이 워커가 해당 건을 선점합니다. 영향 행 수가 1일 때만 발행을 진행합니다.
     *
     * 워커가 겹쳐 돌거나 인스턴스가 여러 개여도 같은 건이 두 번 발행되지 않게 하는 유일한 장치입니다.
     * 조회 시점의 상태를 다시 조건에 넣어 UPDATE 한 문장으로 판정하므로, 두 워커가 동시에 들어와도
     * 뒤늦은 쪽은 0행을 받습니다.
     *
     * 선점은 발행과 <b>다른 트랜잭션</b>에서 즉시 커밋돼야 합니다. 한 트랜잭션으로 묶어도 중복 발행 자체는
     * 막히지만(뒤늦은 UPDATE가 대기 후 조건을 재평가해 0행을 받는다), 그 대기가 폴링 5분 내내 이어져
     * 두 번째 워커가 커넥션을 쥔 채 요청 타임아웃까지 갑니다.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update PublishRecord r
               set r.status = com.van.cardnews.domain.publish.entity.PublishStatus.PROCESSING,
                   r.processingStartedAt = :now
             where r.id = :id
               and r.status in (com.van.cardnews.domain.publish.entity.PublishStatus.SCHEDULED,
                                com.van.cardnews.domain.publish.entity.PublishStatus.PENDING)
            """)
    int claim(Long id, LocalDateTime now);

    /**
     * PROCESSING 상태로 멈춘 건을 <b>재시도 상한 안에서</b> 다시 큐에 올립니다.
     *
     * 워커가 발행 도중 죽는 원인은 대개 앱이 아니라 인프라입니다(인스턴스 메모리 초과로 강제 종료 등).
     * 그런 건을 곧바로 FAILED로 못박으면, Meta가 거절한 건은 자동 재시도되는데 정작 우리 쪽 사고로 죽은 건은
     * 사람이 다시 요청해야만 올라가는 뒤집힌 정책이 됩니다.
     *
     * 무한 재선점은 {@code retryCount} 상한이 막습니다 — 매번 죽는 건은 상한을 소진하고 {@link #failStuck}이
     * FAILED로 정리합니다. 그래서 이 메서드를 먼저 돌리고 failStuck을 뒤에 돌려야 합니다.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update PublishRecord r
               set r.status = com.van.cardnews.domain.publish.entity.PublishStatus.SCHEDULED,
                   r.retryCount = r.retryCount + 1,
                   r.scheduledAt = :retryAt,
                   r.processingStartedAt = null,
                   r.errorMessage = :message
             where r.status = com.van.cardnews.domain.publish.entity.PublishStatus.PROCESSING
               and r.processingStartedAt < :threshold
               and r.retryCount < :maxRetryCount
            """)
    int retryStuck(LocalDateTime threshold, LocalDateTime retryAt, String message, int maxRetryCount);

    /**
     * 재시도 상한까지 소진한 채 PROCESSING으로 멈춘 건을 FAILED로 정리합니다.
     *
     * {@link #retryStuck}을 먼저 돌린 뒤 호출해야 합니다. 그래야 여기 남는 것이 "상한을 다 쓴 건"뿐입니다.
     * 회수된 건은 원인을 모르므로 failure_type 없이 FAILED로 남습니다. 다시 올릴지는 사용자가 판단합니다.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update PublishRecord r
               set r.status = com.van.cardnews.domain.publish.entity.PublishStatus.FAILED,
                   r.errorMessage = :message
             where r.status = com.van.cardnews.domain.publish.entity.PublishStatus.PROCESSING
               and r.processingStartedAt < :threshold
            """)
    int failStuck(LocalDateTime threshold, String message);

    /**
     * 예약 시각이 유예 시간을 넘긴 미실행 건을 실패 처리합니다.
     *
     * 서비스 다운타임 뒤 워커가 다시 돌면, 하루 지난 예약까지 한꺼번에 발행되는 사고를 막습니다.
     * 유예 시간 안이면 지연 발행하고, 넘겼으면 사용자가 의도한 시점이 아니므로 올리지 않습니다.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update PublishRecord r
               set r.status = com.van.cardnews.domain.publish.entity.PublishStatus.FAILED,
                   r.errorMessage = :message
             where r.status in (com.van.cardnews.domain.publish.entity.PublishStatus.SCHEDULED,
                                com.van.cardnews.domain.publish.entity.PublishStatus.PENDING)
               and r.scheduledAt < :deadline
            """)
    int failExpired(LocalDateTime deadline, String message);
}
