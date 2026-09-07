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

public interface PublishRecordRepository
        extends JpaRepository<PublishRecord, Long> {

    List<PublishRecord> findByContentIdOrderByIdDesc(Long contentId);

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
     * PROCESSING 상태로 멈춘 건을 회수합니다.
     *
     * 워커가 발행 도중 죽으면(인스턴스 강제 종료 등) 그 건은 아무도 손대지 않아 영구 정체됩니다.
     * SCHEDULED로 되돌리지 않고 FAILED로 두는 이유는, 되돌리면 매번 죽는 건이 무한히 재선점되기 때문입니다.
     * 재시도는 {@link PublishRecord#retry()}가 FAILED만 받도록 되어 있어 정책(VAN-13)이 판단합니다.
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
