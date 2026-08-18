package com.van.cardnews.domain.approval.repository;

import com.van.cardnews.domain.approval.entity.ApprovalRequest;
import com.van.cardnews.domain.approval.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApprovalRequestRepository
        extends JpaRepository<ApprovalRequest, Long> {

    /**
     * 현재 대기 중인 승인 요청이 존재하는지 확인합니다.
     *
     * 한 콘텐츠에 동시에 여러 개의 PENDING 요청이 생기는 것을 방지할 때 사용합니다.
     */
    boolean existsByContentIdAndStatus(
            Long contentId,
            ApprovalStatus status
    );

    /**
     * 가장 최근 승인 요청을 조회합니다.
     *
     * 게시 가능 여부 판단 시 사용합니다.
     */
    Optional<ApprovalRequest> findTopByContentIdOrderByRequestedAtDesc(
            Long contentId
    );
}
