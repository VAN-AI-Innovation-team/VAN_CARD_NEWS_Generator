package com.van.cardnews.domain.generation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.entity.ContentImage;
import com.van.cardnews.domain.generation.dto.request.CardGenerationRequest;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;
import com.van.cardnews.global.ai.claude.ClaudeClient;
import com.van.cardnews.global.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardGenerationService {

    private final ClaudeClient claudeClient;
    private final CardGenerationValidator cardGenerationValidator;
    private final ImageStorageService imageStorageService;
    private final ImageCropService imageCropService;
    private final ObjectMapper objectMapper;

    public CardGenerationResult generate(Content content) {

        List<CardGenerationRequest.InputImage> images =
                content.getImages()
                        .stream()
                        .map(this::toInputImage)
                        .toList();

        CardGenerationRequest request =
                new CardGenerationRequest(
                        content.getTitle(),
                        content.getBody(),
                        content.getTemplate()
                                .getContentType()
                                .getValue(),
                        content.getTemplate()
                                .getLayoutDefinition(),
                        images
                );

        log.info(
                "[CardGeneration] Claude 요청 - contentId={}, templateId={}, imageCount={}",
                content.getId(),
                content.getTemplate().getId(),
                images.size()
        );

        CardGenerationResult result =
                claudeClient.generateCardContent(request);

        /*
         * Claude가 반환한 최초 결과에는 아직 cropArea가 없습니다.
         * 따라서 텍스트/구조 검증만 먼저 수행합니다.
         */
        cardGenerationValidator.validateInitial(
                result,
                content.getTemplate()
                        .getLayoutDefinition()
        );

        validateSelectedImageIds(
                result,
                content
        );

        /*
         * 템플릿의 cropRatio와 실제 이미지 크기를 기준으로
         * 카드별 cropArea를 계산합니다.
         */
        CardGenerationResult finalResult =
                applyCropAreas(
                        result,
                        content
                );

        /*
         * cropArea까지 포함된 최종 결과를 다시 검증합니다.
         */
        cardGenerationValidator.validateFinal(
                finalResult,
                content.getTemplate()
                        .getLayoutDefinition()
        );

        /*
         * #8의 최종 자동 구성 결과를 저장합니다.
         * 실제 PNG/JPG 생성은 #9에서 수행합니다.
         */
        content.updateCardGenerationResult(
                objectMapper.valueToTree(finalResult)
        );

        log.info(
                "[CardGeneration] 카드 구성 완료 - contentId={}, contentCards={}",
                content.getId(),
                finalResult.content().size()
        );

        return finalResult;
    }

    private CardGenerationResult applyCropAreas(
            CardGenerationResult result,
            Content content
    ) {
        var layout =
                content.getTemplate()
                        .getLayoutDefinition();

        String coverCropRatio =
                layout.path("cards")
                        .path("cover")
                        .path("elements")
                        .path("image")
                        .path("cropRatio")
                        .asText(null);

        String contentCropRatio =
                layout.path("cards")
                        .path("content")
                        .path("elements")
                        .path("image")
                        .path("cropRatio")
                        .asText(null);

        ContentImage coverImage =
                findImage(
                        content,
                        result.cover().imageId()
                );

        CardGenerationResult.CropArea coverCrop =
                imageCropService.calculateCropArea(
                        coverImage,
                        coverCropRatio
                );

        CardGenerationResult.CoverContent cover =
                new CardGenerationResult.CoverContent(
                        result.cover().title(),
                        result.cover().highlight(),
                        result.cover().imageId(),
                        coverCrop
                );

        List<CardGenerationResult.ContentCard> contentCards =
                result.content()
                        .stream()
                        .map(card -> {

                            ContentImage image =
                                    findImage(
                                            content,
                                            card.imageId()
                                    );

                            CardGenerationResult.CropArea crop =
                                    imageCropService
                                            .calculateCropArea(
                                                    image,
                                                    contentCropRatio
                                            );

                            return new CardGenerationResult.ContentCard(
                                    card.title(),
                                    card.body(),
                                    card.highlight(),
                                    card.imageId(),
                                    crop
                            );
                        })
                        .toList();

        return new CardGenerationResult(
                cover,
                contentCards,
                result.closing()
        );
    }

    private ContentImage findImage(
            Content content,
            Long imageId
    ) {
        return content.getImages()
                .stream()
                .filter(image ->
                        image.getId().equals(imageId)
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 이미지입니다. id="
                                        + imageId
                        )
                );
    }

    private CardGenerationRequest.InputImage toInputImage(
            ContentImage image
    ) {
        byte[] imageBytes =
                imageStorageService.read(
                        image.getImageUrl()
                );

        String base64Data =
                Base64.getEncoder()
                        .encodeToString(imageBytes);

        String mediaType =
                imageStorageService.getContentType(
                        image.getImageUrl()
                );

        return new CardGenerationRequest.InputImage(
                image.getId(),
                mediaType,
                base64Data
        );
    }

    private void validateSelectedImageIds(
            CardGenerationResult result,
            Content content
    ) {
        List<Long> imageIds =
                content.getImages()
                        .stream()
                        .map(ContentImage::getId)
                        .toList();

        validateImageId(
                result.cover().imageId(),
                imageIds,
                "cover"
        );

        for (int i = 0;
             i < result.content().size();
             i++) {

            validateImageId(
                    result.content()
                            .get(i)
                            .imageId(),
                    imageIds,
                    "content[" + i + "]"
            );
        }
    }

    private void validateImageId(
            Long imageId,
            List<Long> imageIds,
            String cardName
    ) {
        if (imageId == null) {
            throw new IllegalArgumentException(
                    cardName
                            + "에 이미지가 선택되지 않았습니다."
            );
        }

        if (!imageIds.contains(imageId)) {
            throw new IllegalArgumentException(
                    cardName
                            + "가 존재하지 않는 이미지 ID를 선택했습니다. id="
                            + imageId
            );
        }
    }
}
