package com.van.cardnews.domain.jobhistory.repository;

import com.van.cardnews.domain.jobhistory.entity.JobHistory;
import com.van.cardnews.domain.jobhistory.entity.JobType;
import com.van.cardnews.domain.jobhistory.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobHistoryRepository extends JpaRepository<JobHistory, Long> {
    List<JobHistory> findByContentIdOrderByRequestedAtDesc(Long contentId);

    Optional<JobHistory> findTopByContentIdOrderByRequestedAtDesc(Long contentId);

    Optional<JobHistory> findTopByContentIdAndJobTypeOrderByRequestedAtDesc(
            Long contentId,
            JobType jobType
    );

    boolean existsByContentIdAndStatusIn(
            Long contentId,
            List<JobStatus> statuses
    );
}
