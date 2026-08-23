package com.van.cardnews.domain.content.service;

import com.van.cardnews.domain.approval.entity.ApprovalRequest;
import com.van.cardnews.domain.approval.repository.ApprovalRequestRepository;
import com.van.cardnews.domain.content.dto.response.ContentManagementListResponse;
import com.van.cardnews.domain.content.entity.ContentStatus;
import com.van.cardnews.domain.content.repository.ContentRepository;
import com.van.cardnews.domain.generatedimage.repository.GeneratedCardImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentManagementService {

    private final ContentRepository contentRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final GeneratedCardImageRepository generatedCardImageRepository;

    @Transactional(readOnly = true)
    public List<ContentManagementListResponse> getContents() {
        return contentRepository
                .findByStatusNotOrderByCreatedAtDesc(ContentStatus.ARCHIVED)
                .stream()
                .map(content -> {
                    ApprovalRequest approvalRequest =
                            approvalRequestRepository
                                    .findTopByContentIdOrderByRequestedAtDesc(content.getId())
                                    .orElse(null);

                    int cardCount = (int) generatedCardImageRepository.countByContent_Id(content.getId());

                    return ContentManagementListResponse.from(
                            content,
                            cardCount,
                            approvalRequest
                    );
                })
                .toList();
    }
}
