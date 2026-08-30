package com.van.cardnews.domain.approval.service;

import com.van.cardnews.domain.approval.dto.request.ApprovalRejectRequest;
import com.van.cardnews.domain.approval.dto.response.ApprovalRequestListResponse;
import com.van.cardnews.domain.approval.dto.response.ApprovalRequestResponse;
import com.van.cardnews.domain.approval.entity.ApprovalRequest;
import com.van.cardnews.domain.approval.entity.ApprovalStatus;
import com.van.cardnews.domain.approval.repository.ApprovalRequestRepository;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.repository.ContentRepository;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApprovalRequestService {

    private final ContentRepository contentRepository;
    private final ApprovalRequestRepository approvalRequestRepository;

    /**
     * 콘텐츠 승인 요청을 생성합니다.
     *
     * 중복 방지는 3단계로 보장합니다.
     *
     * 1. Content row에 PESSIMISTIC_WRITE lock 적용
     * 2. PENDING 승인 요청 존재 여부 확인
     * 3. DB partial unique index로 최종 무결성 보장
     */
    @Transactional
    public ApprovalRequestResponse requestApproval(Long contentId) {

        /*
         * 일반 findById()가 아니라 승인 요청 전용 비관적 락 조회를 사용합니다.
         *
         * 동일 contentId로 동시에 승인 요청이 들어오는 경우
         * 먼저 진입한 트랜잭션이 Content row를 잠급니다.
         */
        Content content = contentRepository.findByIdForApproval(contentId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.CONTENT_NOT_FOUND
                        )
                );

        /*
         * 카드뉴스 생성이 완료된 콘텐츠만 승인 요청할 수 있습니다.
         */
        if (content.getCardGenerationResult() == null) {
            throw new CustomException(
                    ErrorCode.CONTENT_NOT_READY_FOR_APPROVAL
            );
        }

        /*
         * 현재 승인 대기 중인 요청이 있다면
         * 동일 콘텐츠에 새로운 대기 요청을 만들지 않습니다.
         *
         * Content row lock이 걸려 있기 때문에
         * 동시에 들어온 요청도 이 검사 구간에서 직렬화됩니다.
         */
        if (approvalRequestRepository.existsByContentIdAndStatus(
                contentId,
                ApprovalStatus.PENDING
        )) {
            throw new CustomException(
                    ErrorCode.APPROVAL_REQUEST_ALREADY_PENDING
            );
        }

        ApprovalRequest approvalRequest =
                ApprovalRequest.create(content, null);

        return ApprovalRequestResponse.from(
                approvalRequestRepository.save(approvalRequest)
        );
    }

    /**
     * 현재 승인 대기 중인 요청 목록을 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<ApprovalRequestListResponse> getPendingRequests() {
        return approvalRequestRepository
                .findByStatusOrderByRequestedAtDesc(
                        ApprovalStatus.PENDING
                )
                .stream()
                .map(ApprovalRequestListResponse::from)
                .toList();
    }

    /**
     * 승인 요청 단건 조회
     */
    @Transactional(readOnly = true)
    public ApprovalRequestResponse getRequest(
            Long approvalRequestId
    ) {
        return ApprovalRequestResponse.from(
                findRequest(approvalRequestId)
        );
    }

    /**
     * 특정 콘텐츠의 전체 승인 이력을 조회합니다.
     *
     * PENDING / APPROVED / REJECTED 상태의
     * 모든 승인 요청을 최신순으로 반환합니다.
     */
    @Transactional(readOnly = true)
    public List<ApprovalRequestListResponse> getHistory(
            Long contentId
    ) {
        /*
         * 존재하지 않는 콘텐츠에 대해서는
         * 승인 이력도 조회할 수 없도록 합니다.
         */
        findContent(contentId);

        return approvalRequestRepository
                .findByContentIdOrderByRequestedAtDesc(contentId)
                .stream()
                .map(ApprovalRequestListResponse::from)
                .toList();
    }

    /**
     * 승인 요청 승인 처리
     */
    @Transactional
    public ApprovalRequestResponse approve(
            Long approvalRequestId
    ) {
        ApprovalRequest request = findRequest(approvalRequestId);

        validatePending(request);

        request.approve(null);

        return ApprovalRequestResponse.from(request);
    }

    /**
     * 승인 요청 반려 처리
     *
     * 반려 사유가 비어 있는 경우 처리하지 않습니다.
     */
    @Transactional
    public ApprovalRequestResponse reject(
            Long approvalRequestId,
            ApprovalRejectRequest rejectRequest
    ) {
        ApprovalRequest request = findRequest(approvalRequestId);

        validatePending(request);

        if (rejectRequest == null
                || rejectRequest.reason() == null
                || rejectRequest.reason().isBlank()) {

            throw new CustomException(
                    ErrorCode.APPROVAL_REJECTION_REASON_REQUIRED
            );
        }

        request.reject(
                null,
                rejectRequest.reason().trim()
        );

        return ApprovalRequestResponse.from(request);
    }

    private Content findContent(Long contentId) {
        return contentRepository.findById(contentId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.CONTENT_NOT_FOUND
                        )
                );
    }

    private ApprovalRequest findRequest(
            Long approvalRequestId
    ) {
        return approvalRequestRepository.findById(approvalRequestId)
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.APPROVAL_REQUEST_NOT_FOUND
                        )
                );
    }

    /**
     * 승인/반려는 PENDING 상태에서만 가능합니다.
     */
    private void validatePending(
            ApprovalRequest request
    ) {
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new CustomException(
                    ErrorCode.APPROVAL_REQUEST_NOT_PENDING
            );
        }
    }
}
