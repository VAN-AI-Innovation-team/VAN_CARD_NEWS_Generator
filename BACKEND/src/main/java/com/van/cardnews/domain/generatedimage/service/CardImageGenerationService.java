package com.van.cardnews.domain.generatedimage.service;

import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.repository.ContentRepository; // 💡 1. 누락된 임포트 추가
import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import com.van.cardnews.domain.generatedimage.repository.GeneratedCardImageRepository;
import com.van.cardnews.domain.jobhistory.entity.JobHistory;
import com.van.cardnews.domain.jobhistory.entity.JobType;
import com.van.cardnews.domain.jobhistory.service.JobHistoryService;
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
    private final CoverSplitService coverSplitService;
    private final ImageStorageService imageStorageService;
    private final JobHistoryService jobHistoryService;

    @Transactional
    public List<GeneratedCardImage> generate(Long contentId, boolean splitCoverIntoThree) {
        Content content = contentRepository.findByIdWithImages(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다: " + contentId));

        if (content.getCardGenerationResult() == null) {
            throw new IllegalStateException(
                    "Claude 생성 결과가 없는 콘텐츠입니다. 먼저 카드 구성이 완료되어야 합니다: " + contentId
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
                    content.getTemplate().getCanvasHeight(),
                    splitCoverIntoThree
            );

            HiggsfieldGenerationResult result = higgsfieldClient.generateCardImages(request);

            List<GeneratedCardImage> saved = new ArrayList<>();
            int sortOrder = 0;

            for (HiggsfieldGenerationResult.GeneratedCard card : result.cards()) {
                if (card.cardType() == HiggsfieldGenerationResult.GeneratedCard.CardType.COVER
                        && splitCoverIntoThree) {
                    List<byte[]> pieces = coverSplitService.splitIntoThree(
                            card.imageBytes(),
                            content.getTemplate().getCanvasWidth(),
                            content.getTemplate().getCanvasHeight()
                    );
                    for (int i = 0; i < pieces.size(); i++) {
                        // 💡 2. 3분할 커버: columnIndex는 i(0,1,2), cardIndex는 0으로 지정
                        saved.add(persist(
                                content, GeneratedCardImage.CardType.COVER, i, 0, sortOrder++,
                                pieces.get(i),
                                content.getTemplate().getCanvasWidth(),
                                content.getTemplate().getCanvasHeight()
                        ));
                    }
                } else {
                    GeneratedCardImage.CardType cardType = mapCardType(card.cardType());
                    // 💡 3. 일반 카드: columnIndex는 0, cardIndex는 card.cardIndex() 지정
                    saved.add(persist(
                            content, cardType, 0, card.cardIndex(), sortOrder++,
                            card.imageBytes(), card.width(), card.height()
                    ));
                }
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
            int columnIndex,
            int cardIndex,
            int sortOrder,
            byte[] imageBytes,
            int width,
            int height
    ) {
        String fileName = "content-" + content.getId() + "-" + cardType.name().toLowerCase()
                + "-" + cardIndex + "-" + UUID.randomUUID() + ".png";
        ImageStorageService.StoredImage stored = imageStorageService.save(imageBytes, fileName);

        // 💡 4. GeneratedCardImage.create 인자 순서에 맞게 정확히 매핑
        GeneratedCardImage entity = GeneratedCardImage.create(
                content, cardType, columnIndex, cardIndex, sortOrder,
                stored.publicUrl(), width, height, stored.storageRef()
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
