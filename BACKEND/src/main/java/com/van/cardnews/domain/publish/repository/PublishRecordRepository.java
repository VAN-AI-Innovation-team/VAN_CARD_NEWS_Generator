package com.van.cardnews.domain.publish.repository;

import com.van.cardnews.domain.publish.entity.PublishRecord;
import com.van.cardnews.domain.publish.entity.PublishStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PublishRecordRepository
        extends JpaRepository<PublishRecord, Long> {

    List<PublishRecord> findByContentIdOrderByIdDesc(Long contentId);

    /**
     * 워커가 실행할 대상을 가져옵니다.
     * idx_publish_records_status_scheduled_at 인덱스를 타는 조회 경로입니다.
     */
    List<PublishRecord> findByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            List<PublishStatus> statuses,
            LocalDateTime scheduledAt
    );
}
