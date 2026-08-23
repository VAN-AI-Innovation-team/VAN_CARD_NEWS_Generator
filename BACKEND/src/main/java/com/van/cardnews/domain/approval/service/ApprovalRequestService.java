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

    @Transactional
    public ApprovalRequestResponse requestApproval(Long contentId) {
        Content content = findContent(contentId);

        if (content.getCardGenerationResult() == null) {
            throw new CustomException(ErrorCode.CONTENT_NOT_READY_FOR_APPROVAL);
        }

        if (approvalRequestRepository.existsByContentIdAndStatus(
                contentId, ApprovalStatus.PENDING)) {
            throw new CustomException(ErrorCode.APPROVAL_REQUEST_ALREADY_PENDING);
        }

        return ApprovalRequestResponse.from(
                approvalRequestRepository.save(ApprovalRequest.create(content, null))
        );
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestListResponse> getPendingRequests() {
        return approvalRequestRepository
                .findByStatusOrderByRequestedAtDesc(ApprovalStatus.PENDING)
                .stream()
                .map(ApprovalRequestListResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApprovalRequestResponse getRequest(Long approvalRequestId) {
        return ApprovalRequestResponse.from(findRequest(approvalRequestId));
    }

    @Transactional
    public ApprovalRequestResponse approve(Long approvalRequestId) {
        ApprovalRequest request = findRequest(approvalRequestId);
        validatePending(request);

        request.approve(null);
        return ApprovalRequestResponse.from(request);
    }

    @Transactional
    public ApprovalRequestResponse reject(
            Long approvalRequestId,
            ApprovalRejectRequest rejectRequest
    ) {
        ApprovalRequest request = findRequest(approvalRequestId);
        validatePending(request);

        if (rejectRequest.reason() == null || rejectRequest.reason().isBlank()) {
            throw new CustomException(ErrorCode.APPROVAL_REJECTION_REASON_REQUIRED);
        }

        request.reject(null, rejectRequest.reason().trim());
        return ApprovalRequestResponse.from(request);
    }

    private Content findContent(Long contentId) {
        return contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));
    }

    private ApprovalRequest findRequest(Long approvalRequestId) {
        return approvalRequestRepository.findById(approvalRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPROVAL_REQUEST_NOT_FOUND));
    }

    private void validatePending(ApprovalRequest request) {
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new CustomException(ErrorCode.APPROVAL_REQUEST_NOT_PENDING);
        }
    }
}
