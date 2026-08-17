package com.van.cardnews.domain.jobhistory.repository;

import com.van.cardnews.domain.jobhistory.entity.JobHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobHistoryRepository extends JpaRepository<JobHistory, Long> {
    List<JobHistory> findByContentIdOrderByRequestedAtDesc(Long contentId);
}
