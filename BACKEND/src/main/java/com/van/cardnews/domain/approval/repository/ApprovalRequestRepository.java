package com.van.cardnews.domain.approval.repository;

import com.van.cardnews.domain.approval.entity.ApprovalRequest;
import com.van.cardnews.domain.approval.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    boolean existsByContentIdAndStatus(Long contentId, ApprovalStatus status);

    Optional<ApprovalRequest> findTopByContentIdOrderByRequestedAtDesc(Long contentId);

    List<ApprovalRequest> findByStatusOrderByRequestedAtDesc(ApprovalStatus status);
}
