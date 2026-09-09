package com.van.cardnews.domain.content.service;

import com.van.cardnews.domain.approval.entity.ApprovalRequest;
import com.van.cardnews.domain.approval.repository.ApprovalRequestRepository;
import com.van.cardnews.domain.content.dto.response.ContentManagementListResponse;
import com.van.cardnews.domain.content.dto.response.ContentHistoryPageResponse;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.entity.ContentStatus;
import com.van.cardnews.domain.content.repository.ContentRepository;
import com.van.cardnews.domain.generatedimage.repository.GeneratedCardImageRepository;
import com.van.cardnews.domain.jobhistory.entity.JobHistory;
import com.van.cardnews.domain.jobhistory.entity.JobStatus;
import com.van.cardnews.domain.jobhistory.entity.JobType;
import com.van.cardnews.domain.jobhistory.repository.JobHistoryRepository;
import com.van.cardnews.domain.publish.entity.PublishRecord;
import com.van.cardnews.domain.publish.repository.PublishRecordRepository;
import com.van.cardnews.domain.publish.service.InstagramPublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentManagementService {

    private final ContentRepository contentRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final GeneratedCardImageRepository generatedCardImageRepository;
    private final JobHistoryRepository jobHistoryRepository;
    private final PublishRecordRepository publishRecordRepository;

    @Transactional(readOnly = true)
    public List<ContentManagementListResponse> getContents() {
        return contentRepository
                .findByStatusNotOrderByCreatedAtDesc(ContentStatus.ARCHIVED)
                .stream()
                .map(this::toListResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContentHistoryPageResponse getContentHistory(Pageable pageable) {
        Page<Content> contentPage = contentRepository.findByStatusNot(ContentStatus.ARCHIVED, pageable);

        List<ContentManagementListResponse> contents = contentPage
                .getContent()
                .stream()
                .map(this::toListResponse)
                .toList();

        return new ContentHistoryPageResponse(
                contents,
                contentPage.getNumber(),
                contentPage.getSize(),
                contentPage.getTotalElements(),
                contentPage.getTotalPages(),
                contentPage.isFirst(),
                contentPage.isLast()
        );
    }

    private String resolveGenerationStatus(Content content) {
        Long contentId = content.getId();

        var latestJob = jobHistoryRepository
                .findTopByContentIdOrderByRequestedAtDesc(contentId);

        if (latestJob.isEmpty()) {
            return null;
        }

        JobHistory latest = latestJob.get();
        var latestImageJob = jobHistoryRepository
                .findTopByContentIdAndJobTypeOrderByRequestedAtDesc(contentId, JobType.IMAGE_GENERATION);

        // 가장 최근 작업이 이미지 생성이라면 이미지 작업 상태를 우선합니다.
        if (latestImageJob.isPresent()
                && !latestImageJob.get().getRequestedAt().isBefore(latest.getRequestedAt())) {
            return latestImageJob.get().getStatus().name();
        }

        // 카드 구성은 완료됐지만 아직 이미지 생성 작업이 시작되지 않은 상태입니다.
        if (latest.getJobType() == JobType.FULL_PIPELINE
                && latest.getStatus() == JobStatus.COMPLETED
                && content.getCardGenerationResult() != null
                && generatedCardImageRepository.countByContent_Id(contentId) == 0) {
            return "IMAGE_PENDING";
        }

        return latest.getStatus().name();
    }

    private ContentManagementListResponse toListResponse(Content content) {
        ApprovalRequest approvalRequest =
                approvalRequestRepository
                        .findTopByContentIdOrderByRequestedAtDesc(content.getId())
                        .orElse(null);

        int cardCount = (int) generatedCardImageRepository.countByContent_Id(content.getId());

        String generationStatus = resolveGenerationStatus(content);

        PublishRecord publishRecord = publishRecordRepository
                .findTopByContentIdAndChannelOrderByIdDesc(
                        content.getId(), InstagramPublishService.CHANNEL)
                .orElse(null);

        return ContentManagementListResponse.from(
                content,
                cardCount,
                approvalRequest,
                generationStatus,
                publishRecord
        );
    }

}
