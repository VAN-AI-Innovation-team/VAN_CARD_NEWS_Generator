package com.van.cardnews.domain.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.van.cardnews.domain.content.dto.request.CardImagePlacementUpdateRequest;
import com.van.cardnews.domain.content.dto.request.ContentCreateRequest;
import com.van.cardnews.domain.content.dto.request.ContentEditRequest;
import com.van.cardnews.domain.content.dto.request.ContentPreviewUpdateRequest;
import com.van.cardnews.domain.content.dto.response.ContentCreateResponse;
import com.van.cardnews.domain.content.dto.response.ContentPreviewResponse;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.entity.ContentImage;
import com.van.cardnews.domain.content.repository.ContentRepository;
import com.van.cardnews.domain.approval.repository.ApprovalRequestRepository;
import com.van.cardnews.domain.generatedimage.repository.GeneratedCardImageRepository;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;
import com.van.cardnews.domain.generation.service.CardGenerationValidator;
import com.van.cardnews.domain.jobhistory.entity.JobHistory;
import com.van.cardnews.domain.jobhistory.entity.JobType;
import com.van.cardnews.domain.jobhistory.service.JobHistoryService;
import com.van.cardnews.domain.template.entity.Template;
import com.van.cardnews.domain.template.repository.TemplateRepository;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import com.van.cardnews.global.pipeline.ContentGenerationRequestedEvent;
import com.van.cardnews.global.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    private static final int MAX_IMAGE_COUNT = 10;

    private final ContentRepository contentRepository;
    private final JobHistoryService jobHistoryService;
    private final ImageStorageService imageStorageService;
    private final TemplateRepository templateRepository;
    private final CardGenerationValidator cardGenerationValidator;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final GeneratedCardImageRepository generatedCardImageRepository;

    @Transactional
    public ContentCreateResponse createContent(
            ContentCreateRequest request,
            List<MultipartFile> images
    ) {
        validateImageCount(images);

        Template template =
                templateRepository
                        .findByIdAndActiveTrue(
                                request.templateId()
                        )
                        .orElseThrow(() ->
                                new CustomException(
                                        ErrorCode.TEMPLATE_NOT_FOUND
                                )
                        );

        Content content =
                Content.create(
                        request.title(),
                        request.body(),
                        template
                );

        attachImages(content, images);

        contentRepository.save(content);

        JobHistory jobHistory =
                jobHistoryService.createJobHistory(
                        content,
                        JobType.FULL_PIPELINE
                );

        /*
         * Content와 JobHistory가 저장된 현재 트랜잭션이
         * 정상적으로 COMMIT된 이후 Pipeline이 실행됩니다.
         */
        eventPublisher.publishEvent(
                new ContentGenerationRequestedEvent(
                        content.getId(),
                        jobHistory.getId()
                )
        );

        log.info(
                "콘텐츠 생성 요청 접수 완료 - contentId={}, jobHistoryId={}, templateId={}, templateCode={}, templateVersion={}",
                content.getId(),
                jobHistory.getId(),
                template.getId(),
                template.getCode(),
                template.getVersion()
        );

        return ContentCreateResponse.of(
                content,
                jobHistory
        );
    }

    @Transactional(readOnly = true)
    public ContentPreviewResponse getPreview(
            Long contentId
    ) {
        Content content =
                contentRepository.findById(contentId)
                        .orElseThrow(() ->
                                new CustomException(
                                        ErrorCode.CONTENT_NOT_FOUND
                                )
                        );

        return ContentPreviewResponse.from(
                content,
                approvalRequestRepository.findTopByContentIdOrderByRequestedAtDesc(contentId).orElse(null)
        );
    }

    @Transactional
    public ContentCreateResponse editContent(
            Long contentId,
            ContentEditRequest request,
            List<MultipartFile> newImages
    ) {
        validateImageCount(newImages);

        Content content = contentRepository.findByIdWithImages(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));

        Template template = templateRepository.findByIdAndActiveTrue(request.templateId())
                .orElseThrow(() -> new CustomException(ErrorCode.TEMPLATE_NOT_FOUND));

        java.util.Set<Long> keepImageIds = request.keepImageIds() == null
                ? java.util.Set.of()
                : new java.util.HashSet<>(request.keepImageIds());

        boolean allKeepIdsBelongToContent = content.getImages().stream()
                .filter(image -> keepImageIds.contains(image.getId()))
                .count() == keepImageIds.size();
        if (!allKeepIdsBelongToContent) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "해당 콘텐츠에 존재하지 않는 이미지가 포함되어 있습니다.");
        }

        content.removeImagesNotIn(keepImageIds);
        attachImages(content, newImages);
        if (content.getImages().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "사진을 최소 1장 이상 유지하거나 추가해주세요.");
        }
        content.updateBasicInfo(request.title().trim(), request.body().trim(), template);

        generatedCardImageRepository.deleteByContent_Id(contentId);

        JobHistory jobHistory = jobHistoryService.createJobHistory(content, JobType.FULL_PIPELINE);
        eventPublisher.publishEvent(new ContentGenerationRequestedEvent(contentId, jobHistory.getId()));

        log.info("콘텐츠 수정 후 재생성 요청 - contentId={}, jobHistoryId={}, templateId={}",
                contentId, jobHistory.getId(), template.getId());

        return ContentCreateResponse.of(content, jobHistory);
    }

    @Transactional
    public ContentPreviewResponse updatePreview(
            Long contentId,
            ContentPreviewUpdateRequest request
    ) {
        Content content =
                contentRepository.findById(contentId)
                        .orElseThrow(() ->
                                new CustomException(
                                        ErrorCode.CONTENT_NOT_FOUND
                                )
                        );

        if (content.getCardGenerationResult() == null) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT,
                    "아직 카드 구성 결과가 생성되지 않았습니다."
            );
        }

        // JsonNode 형태의 결과를 CardGenerationResult 객체로 변환하여 검증에 전달
        CardGenerationResult resultObj =
                objectMapper.convertValue(
                        request.cardGenerationResult(),
                        CardGenerationResult.class
                );

        CardGenerationValidator.validateJson(
                resultObj,
                content.getTemplate().getLayoutDefinition()
        );

        content.updateCardGenerationResult(
                request.cardGenerationResult()
        );

        return ContentPreviewResponse.from(
                content,
                approvalRequestRepository.findTopByContentIdOrderByRequestedAtDesc(contentId).orElse(null)
        );
    }

    @Transactional
    public ContentPreviewResponse updateImageCrops(
            Long contentId,
            CardImagePlacementUpdateRequest request
    ) {
        Content content =
                contentRepository.findById(contentId)
                        .orElseThrow(() ->
                                new CustomException(
                                        ErrorCode.CONTENT_NOT_FOUND
                                )
                        );

        ArrayNode placements =
                objectMapper.createArrayNode();

        for (
                CardImagePlacementUpdateRequest.Placement placement
                : request.placements()
        ) {
            boolean imageExists =
                    content.getImages()
                            .stream()
                            .anyMatch(image ->
                                    image.getId()
                                            .equals(
                                                    placement.imageId()
                                            )
                            );

            if (!imageExists) {
                throw new CustomException(
                        ErrorCode.INVALID_INPUT,
                        "해당 콘텐츠에 존재하지 않는 이미지입니다. id="
                                + placement.imageId()
                );
            }

            ObjectNode item =
                    objectMapper.createObjectNode();

            item.put(
                    "cardType",
                    placement.cardType()
            );

            item.put(
                    "cardIndex",
                    placement.cardIndex()
            );

            item.put(
                    "imageId",
                    placement.imageId()
            );

            ObjectNode cropArea =
                    objectMapper.createObjectNode();

            cropArea.put(
                    "x",
                    placement.cropArea().x()
            );

            cropArea.put(
                    "y",
                    placement.cropArea().y()
            );

            cropArea.put(
                    "width",
                    placement.cropArea().width()
            );

            cropArea.put(
                    "height",
                    placement.cropArea().height()
            );

            item.set(
                    "cropArea",
                    cropArea
            );

            placements.add(item);
        }

        content.updateCardImagePlacements(
                placements
        );

        return ContentPreviewResponse.from(
                content,
                approvalRequestRepository.findTopByContentIdOrderByRequestedAtDesc(contentId).orElse(null)
        );
    }

    private void validateImageCount(
            List<MultipartFile> images
    ) {
        if (images != null &&
                images.size() > MAX_IMAGE_COUNT) {

            throw new CustomException(
                    ErrorCode.TOO_MANY_IMAGES
            );
        }
    }

    private void attachImages(
            Content content,
            List<MultipartFile> images
    ) {
        if (images == null ||
                images.isEmpty()) {
            return;
        }

        int order = content.getImages().size();

        for (MultipartFile image : images) {
            String imageUrl =
                    imageStorageService.store(image);

            content.addImage(
                    ContentImage.create(
                            imageUrl,
                            order++
                    )
            );
        }
    }
}
