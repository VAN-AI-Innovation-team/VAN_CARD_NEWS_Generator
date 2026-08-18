package com.van.cardnews.domain.publish.repository;

import com.van.cardnews.domain.publish.entity.PublishRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublishRecordRepository
        extends JpaRepository<PublishRecord, Long> {

    List<PublishRecord> findByContentIdOrderByIdDesc(
            Long contentId
    );
}
