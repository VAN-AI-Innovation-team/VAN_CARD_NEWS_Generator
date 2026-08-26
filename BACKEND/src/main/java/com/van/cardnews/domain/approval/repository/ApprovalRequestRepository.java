package com.van.cardnews.domain.approval.repository;

import com.van.cardnews.domain.approval.entity.ApprovalRequest;
import com.van.cardnews.domain.approval.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository
        extends JpaRepository<ApprovalRequest, Long> {

    /**
     * 특정 콘텐츠에 특정 상태의 승인 요청이 존재하는지 확인합니다.
     *
     * 승인 요청 생성 시 PENDING 중복 방지에 사용합니다.
     */
    boolean existsByContentIdAndStatus(
            Long contentId,
            ApprovalStatus status
    );

    /**
     * 특정 콘텐츠의 가장 최근 승인 요청을 조회합니다.
     */
    Optional<ApprovalRequest> findTopByContentIdOrderByRequestedAtDesc(
            Long contentId
    );

    /**
     * 특정 상태의 승인 요청을 최신순으로 조회합니다.
     */
    List<ApprovalRequest> findByStatusOrderByRequestedAtDesc(
            ApprovalStatus status
    );

    /**
     * 특정 콘텐츠의 전체 승인 요청 이력을 최신순으로 조회합니다.
     *
     * 승인 → 반려 → 재승인 요청 등의 모든 이력을 확인할 수 있습니다.
     */
    List<ApprovalRequest> findByContentIdOrderByRequestedAtDesc(
            Long contentId
    );
}
