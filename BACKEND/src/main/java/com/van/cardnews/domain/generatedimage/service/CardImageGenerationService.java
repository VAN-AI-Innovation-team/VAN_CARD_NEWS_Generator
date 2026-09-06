package com.van.cardnews.domain.generatedimage.service;

import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.repository.ContentRepository; // 💡 1. 누락된 임포트 추가
import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import com.van.cardnews.domain.generatedimage.repository.GeneratedCardImageRepository;
import com.van.cardnews.domain.jobhistory.entity.JobHistory;
import com.van.cardnews.domain.jobhistory.entity.JobType;
import com.van.cardnews.domain.jobhistory.service.JobHistoryService;
import com.van.cardnews.global.image.ImageBytes;
import com.van.cardnews.global.storage.ImageStorageService;
import com.van.cardnews.global.ai.higgsfield.HiggsfieldClient;
import com.van.cardnews.global.ai.higgsfield.dto.HiggsfieldGenerationRequest;
import com.van.cardnews.global.ai.higgsfield.dto.HiggsfieldGenerationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CardImageGenerationService {

    private final ContentRepository contentRepository;
    private final GeneratedCardImageRepository generatedCardImageRepository;
    private final HiggsfieldClient higgsfieldClient;
    private final ImagePlacementResolver imagePlacementResolver;
    private final ImageStorageService imageStorageService;
    private final JobHistoryService jobHistoryService;

    @Transactional
    public List<GeneratedCardImage> generate(Long contentId) {
        Content content = contentRepository.findByIdWithImages(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다: " + contentId));

        if (content.getCardGenerationResult() == null) {
            throw new IllegalStateException(
                    "OpenAI 생성 결과가 없는 콘텐츠입니다. 먼저 카드 구성이 완료되어야 합니다: " + contentId
            );
        }

        JobHistory jobHistory = jobHistoryService.createJobHistory(content, JobType.IMAGE_GENERATION);
        jobHistory.markProcessing();

        try {
            generatedCardImageRepository.deleteByContent_Id(contentId);

            HiggsfieldGenerationRequest request = new HiggsfieldGenerationRequest(
                    content.getCardGenerationResult(),
                    imagePlacementResolver.resolve(content),
                    content.getTemplate().getLayoutDefinition(),
                    content.getTemplate().getDesignTokens(),
                    content.getTemplate().getCanvasWidth(),
                    content.getTemplate().getCanvasHeight()
            );

            HiggsfieldGenerationResult result = higgsfieldClient.generateCardImages(request);

            List<GeneratedCardImage> saved = new ArrayList<>();
            int sortOrder = 0;

            for (HiggsfieldGenerationResult.GeneratedCard card : result.cards()) {
                GeneratedCardImage.CardType cardType = mapCardType(card.cardType());
                saved.add(persist(
                        content, cardType, card.cardIndex(), sortOrder++, card.imageBytes()
                ));
            }

            String representativeUrl = saved.isEmpty() ? null : saved.get(0).getImageUrl();
            jobHistory.markCompleted(representativeUrl);

            return saved;

        } catch (Exception e) {
            jobHistory.markFailed(e.getMessage());
            throw e;
        }
    }

    private GeneratedCardImage persist(
            Content content,
            GeneratedCardImage.CardType cardType,
            int cardIndex,
            int sortOrder,
            byte[] imageBytes
    ) {
        // Instagram은 알파 없는 sRGB JPEG만 받고 폭 상한도 있다. 발행 시점에 변환하면
        // 원본과 변환본을 따로 들고 있어야 하므로, 모든 카드 바이트가 지나가는 이 지점에서
        // 한 번만 변환한다. 크기는 변환 결과에서 읽는다 — 리사이즈되면 값이 달라진다.
        ImageBytes.Jpeg jpeg = ImageBytes.toJpeg(imageBytes);

        String fileName = "content-" + content.getId() + "-" + cardType.name().toLowerCase()
                + "-" + cardIndex + "-" + UUID.randomUUID() + ImageBytes.extension(jpeg.bytes());
        ImageStorageService.StoredImage stored = imageStorageService.save(jpeg.bytes(), fileName);

        // 💡 4. GeneratedCardImage.create 인자 순서에 맞게 정확히 매핑
        GeneratedCardImage entity = GeneratedCardImage.create(
                content, cardType, cardIndex, sortOrder,
                stored.publicUrl(), jpeg.width(), jpeg.height(), stored.storageRef()
        );
        return generatedCardImageRepository.save(entity);
    }

    private GeneratedCardImage.CardType mapCardType(
            HiggsfieldGenerationResult.GeneratedCard.CardType higgsfieldCardType
    ) {
        return switch (higgsfieldCardType) {
            case COVER -> GeneratedCardImage.CardType.COVER;
            case CONTENT -> GeneratedCardImage.CardType.CONTENT;
            case CLOSING -> GeneratedCardImage.CardType.CLOSING;
        };
    }
}
