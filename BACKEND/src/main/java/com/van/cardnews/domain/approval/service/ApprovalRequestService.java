package com.van.cardnews.domain.approval.service;

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

@Service
@RequiredArgsConstructor
public class ApprovalRequestService {

    private final ContentRepository contentRepository;
    private final ApprovalRequestRepository approvalRequestRepository;

    @Transactional
    public ApprovalRequestResponse requestApproval(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));

        if (content.getCardGenerationResult() == null) {
            throw new CustomException(ErrorCode.CONTENT_NOT_READY_FOR_APPROVAL);
        }

        if (approvalRequestRepository.existsByContentIdAndStatus(
                contentId,
                ApprovalStatus.PENDING
        )) {
            throw new CustomException(ErrorCode.APPROVAL_REQUEST_ALREADY_PENDING);
        }

        ApprovalRequest request = ApprovalRequest.create(
                content,
                null
        );

        ApprovalRequest saved = approvalRequestRepository.save(request);

        return ApprovalRequestResponse.from(saved);
    }
}
