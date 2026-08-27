package com.van.cardnews.domain.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.van.cardnews.domain.content.dto.request.CardImagePlacementUpdateRequest;
import com.van.cardnews.domain.content.dto.request.ContentCreateRequest;
import com.van.cardnews.domain.content.dto.request.ContentEditRequest;
import com.van.cardnews.domain.content.dto.request.ContentPreviewUpdateRequest;
import com.van.cardnews.domain.content.dto.request.ContentTemplateUpdateRequest;
import com.van.cardnews.domain.content.dto.request.CardRegenerationRequest;
import com.van.cardnews.domain.content.dto.request.HighlightUpdateRequest;
import com.van.cardnews.domain.content.dto.response.ContentCreateResponse;
import com.van.cardnews.domain.content.dto.response.ContentPreviewResponse;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.entity.ContentImage;
import com.van.cardnews.domain.content.repository.ContentRepository;
import com.van.cardnews.domain.approval.repository.ApprovalRequestRepository;
import com.van.cardnews.domain.generatedimage.repository.GeneratedCardImageRepository;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;
import com.van.cardnews.domain.generation.service.CardGenerationValidator;
import com.van.cardnews.domain.generation.service.CardGenerationService;
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
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final GeneratedCardImageRepository generatedCardImageRepository;
    private final CardGenerationService cardGenerationService;

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

    /**
     * 기존 콘텐츠의 입력값을 그대로 복제합니다.
     *
     * 제목/본문/템플릿/원본 이미지와 이미지 크롭 정보만 복사하고
     * 기존 생성 결과 및 생성 이미지는 복사하지 않습니다.
     * regenerate=true인 경우 새 콘텐츠에 대해 전체 생성 파이프라인을 즉시 실행합니다.
     */
    @Transactional
    public ContentCreateResponse cloneContent(
            Long contentId,
            boolean regenerate
    ) {
        Content source = contentRepository.findByIdWithImages(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));

        Content cloned = Content.create(
                source.getTitle(),
                source.getBody(),
                source.getTemplate()
        );

        for (ContentImage sourceImage : source.getImages()) {
            cloned.addImage(
                    ContentImage.copyOf(sourceImage, cloned.getImages().size())
            );
        }

        contentRepository.save(cloned);

        if (!regenerate) {
            log.info("콘텐츠 복제 완료 - sourceContentId={}, clonedContentId={}, regenerate=false",
                    contentId, cloned.getId());
            return ContentCreateResponse.of(cloned);
        }

        JobHistory jobHistory = jobHistoryService.createJobHistory(
                cloned,
                JobType.FULL_PIPELINE
        );

        eventPublisher.publishEvent(
                new ContentGenerationRequestedEvent(
                        cloned.getId(),
                        jobHistory.getId()
                )
        );

        log.info("콘텐츠 복제 후 즉시 재생성 요청 - sourceContentId={}, clonedContentId={}, jobHistoryId={}",
                contentId, cloned.getId(), jobHistory.getId());

        return ContentCreateResponse.of(cloned, jobHistory);
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
    public ContentPreviewResponse updateTemplate(
            Long contentId,
            ContentTemplateUpdateRequest request
    ) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));

        Template template = templateRepository.findByIdAndActiveTrue(request.templateId())
                .orElseThrow(() -> new CustomException(ErrorCode.TEMPLATE_NOT_FOUND));

        if (content.getTemplate().getContentType() != template.getContentType()) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT,
                    "현재 콘텐츠 유형과 다른 템플릿은 선택할 수 없습니다."
            );
        }

        if (content.getCardGenerationResult() == null) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT,
                    "아직 카드 구성 결과가 생성되지 않았습니다."
            );
        }

        CardGenerationResult result = objectMapper.convertValue(
                content.getCardGenerationResult(),
                CardGenerationResult.class
        );

        try {
            CardGenerationValidator.validateJson(
                    result,
                    template.getLayoutDefinition()
            );
        } catch (IllegalArgumentException e) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT,
                    "현재 카드 내용이 선택한 템플릿에 맞지 않습니다: " + e.getMessage()
            );
        }

        content.updateTemplate(template);
        content.updateCardImagePlacements(objectMapper.createArrayNode());
        generatedCardImageRepository.deleteByContent_Id(contentId);

        return ContentPreviewResponse.from(
                content,
                approvalRequestRepository.findTopByContentIdOrderByRequestedAtDesc(contentId).orElse(null)
        );
    }

    @Transactional
    public ContentPreviewResponse regenerateCard(
            Long contentId,
            CardRegenerationRequest request
    ) {
        Content content = contentRepository.findByIdWithImages(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));

        if (content.getCardGenerationResult() == null) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT,
                    "아직 카드 구성 결과가 생성되지 않았습니다."
            );
        }

        String cardType = request.cardType().trim().toLowerCase();
        if (!cardType.equals("cover") && !cardType.equals("content") && !cardType.equals("closing")) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "지원하지 않는 카드 유형입니다.");
        }

        CardGenerationResult current = objectMapper.convertValue(
                content.getCardGenerationResult(),
                CardGenerationResult.class
        );

        if (cardType.equals("content") &&
                (request.cardIndex() < 0 || request.cardIndex() >= current.content().size())) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "존재하지 않는 본문 카드입니다.");
        }
        if (!cardType.equals("content") && request.cardIndex() != 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "표지/마무리 카드의 인덱스는 0이어야 합니다.");
        }

        CardGenerationResult regenerated = cardGenerationService.regenerateCard(
                content, current, cardType, request.cardIndex(), request.instruction().trim()
        );

        content.updateCardGenerationResult(objectMapper.valueToTree(regenerated));
        content.updateCardImagePlacements(objectMapper.createArrayNode());
        generatedCardImageRepository.deleteByContent_Id(contentId);

        return ContentPreviewResponse.from(
                content,
                approvalRequestRepository.findTopByContentIdOrderByRequestedAtDesc(contentId).orElse(null)
        );
    }

    @Transactional
    public ContentPreviewResponse updateHighlight(
            Long contentId,
            HighlightUpdateRequest request
    ) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));

        if (content.getCardGenerationResult() == null) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT,
                    "아직 카드 구성 결과가 생성되지 않았습니다."
            );
        }

        ObjectNode updated = content.getCardGenerationResult().deepCopy();
        String cardType = request.cardType().name();
        String highlight = request.highlight() == null
                ? null
                : request.highlight().trim();

        try {
            if ("COVER".equals(cardType)) {
                updated.with("cover").put("highlight", highlight);
            } else if ("CONTENT".equals(cardType)) {
                ArrayNode cards = updated.withArray("content");

                if (request.cardIndex() < 0 || request.cardIndex() >= cards.size()) {
                    throw new IllegalArgumentException(
                            "존재하지 않는 본문 카드 인덱스입니다: " + request.cardIndex()
                    );
                }

                ((ObjectNode) cards.get(request.cardIndex()))
                        .put("highlight", highlight);
            } else {
                throw new IllegalArgumentException(
                        "highlight를 수정할 수 없는 카드 유형입니다: " + cardType
                );
            }

            // 하이라이트 수정도 최종 카드 구성 결과 전체 검증을 동일하게 사용합니다.
            CardGenerationResult updatedResult = objectMapper.convertValue(
                    updated,
                    CardGenerationResult.class
            );
            CardGenerationValidator.validateJson(
                    updatedResult,
                    content.getTemplate().getLayoutDefinition()
            );
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT, e.getMessage());
        }

        content.updateCardGenerationResult(updated);
        generatedCardImageRepository.deleteByContent_Id(contentId);

        return ContentPreviewResponse.from(
                content,
                approvalRequestRepository.findTopByContentIdOrderByRequestedAtDesc(contentId).orElse(null)
        );
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

        try {
            CardGenerationValidator.validateJson(
                    resultObj,
                    content.getTemplate().getLayoutDefinition()
            );
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT, e.getMessage());
        }

        content.updateCardGenerationResult(
                request.cardGenerationResult()
        );
        generatedCardImageRepository.deleteByContent_Id(contentId);
        content.updateCardImagePlacements(objectMapper.createArrayNode());

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
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));

        if (content.getCardGenerationResult() == null) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT,
                    "아직 카드 구성 결과가 생성되지 않았습니다."
            );
        }

        CardGenerationResult currentResult = objectMapper.convertValue(
                content.getCardGenerationResult(),
                CardGenerationResult.class
        );

        ArrayNode placements = objectMapper.createArrayNode();
        ObjectNode updatedResult = content.getCardGenerationResult().deepCopy();

        for (CardImagePlacementUpdateRequest.Placement placement : request.placements()) {
            String cardType = placement.cardType().trim().toLowerCase();

            if (!cardType.equals("cover")
                    && !cardType.equals("content")
                    && !cardType.equals("closing")) {
                throw new CustomException(
                        ErrorCode.INVALID_INPUT,
                        "지원하지 않는 카드 유형입니다: " + placement.cardType()
                );
            }

            validateCropArea(placement.cropArea());

            boolean imageExists = content.getImages().stream()
                    .anyMatch(image -> image.getId().equals(placement.imageId()));

            if (!imageExists) {
                throw new CustomException(
                        ErrorCode.INVALID_INPUT,
                        "해당 콘텐츠에 존재하지 않는 이미지입니다. id=" + placement.imageId()
                );
            }

            validateCardIndex(currentResult, cardType, placement.cardIndex());

            ObjectNode item = objectMapper.createObjectNode();
            item.put("cardType", cardType);
            item.put("cardIndex", placement.cardIndex());
            item.put("imageId", placement.imageId());

            ObjectNode cropArea = objectMapper.createObjectNode();
            cropArea.put("x", placement.cropArea().x());
            cropArea.put("y", placement.cropArea().y());
            cropArea.put("width", placement.cropArea().width());
            cropArea.put("height", placement.cropArea().height());

            item.set("cropArea", cropArea);
            placements.add(item);

            updateResultCropArea(
                    updatedResult,
                    cardType,
                    placement.cardIndex(),
                    placement.imageId(),
                    cropArea
            );
        }

        content.updateCardImagePlacements(placements);
        content.updateCardGenerationResult(updatedResult);
        generatedCardImageRepository.deleteByContent_Id(contentId);

        return ContentPreviewResponse.from(
                content,
                approvalRequestRepository.findTopByContentIdOrderByRequestedAtDesc(contentId).orElse(null)
        );
    }

    private void validateCardIndex(
            CardGenerationResult result,
            String cardType,
            int cardIndex
    ) {
        if ("cover".equals(cardType) || "closing".equals(cardType)) {
            if (cardIndex != 0) {
                throw new CustomException(
                        ErrorCode.INVALID_INPUT,
                        cardType + " 카드의 인덱스는 0이어야 합니다."
                );
            }
            return;
        }

        if (cardIndex >= result.content().size()) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT,
                    "존재하지 않는 본문 카드 인덱스입니다: " + cardIndex
            );
        }
    }

    private void validateCropArea(
            CardImagePlacementUpdateRequest.CropArea cropArea
    ) {
        if (cropArea.x() + cropArea.width() > 100.0
                || cropArea.y() + cropArea.height() > 100.0) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT,
                    "크롭 영역이 원본 이미지 범위를 벗어났습니다."
            );
        }
    }

    private void updateResultCropArea(
            ObjectNode result,
            String cardType,
            int cardIndex,
            long imageId,
            ObjectNode cropArea
    ) {
        ObjectNode card;

        if ("cover".equals(cardType)) {
            card = (ObjectNode) result.with("cover");
        } else if ("closing".equals(cardType)) {
            card = (ObjectNode) result.with("closing");
        } else {
            ArrayNode cards = result.withArray("content");
            card = (ObjectNode) cards.get(cardIndex);
        }

        card.put("imageId", imageId);
        card.set("cropArea", cropArea.deepCopy());
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
