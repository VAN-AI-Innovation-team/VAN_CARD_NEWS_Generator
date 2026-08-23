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
    private final ObjectMapper objectMapper;

    public CardGenerationResult generate(
            Content content
    ) {
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
                claudeClient.generateCardContent(
                        request
                );

        /*
         * Claude가 반환한 카드 구성 결과를 검증합니다.
         *
         * 여기서는:
         * - cover
         * - content[]
         * - closing
         * - 텍스트 길이
         *
         * 등을 검증합니다.
         *
         * 카드 이미지의 실제 생성과 crop은
         * 이후 Higgsfield 단계에서 처리합니다.
         */
        cardGenerationValidator.validateInitial(
                result,
                content.getTemplate()
                        .getLayoutDefinition()
        );

        /*
         * Claude가 선택한 imageId가
         * 실제 ContentImage에 존재하는지 검증합니다.
         */
        validateSelectedImageIds(
                result,
                content
        );

        /*
         * 중요:
         * Claude 결과를 그대로 저장합니다.
         */
        content.updateCardGenerationResult(
                objectMapper.valueToTree(result)
        );

        log.info(
                "[CardGeneration] 카드 구성 완료 - contentId={}, contentCards={}",
                content.getId(),
                result.content().size()
        );

        return result;
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
                        .encodeToString(
                                imageBytes
                        );

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

        // closing에 이미지 요소가 있는 최신 템플릿에서는
        // 마무리 카드도 실제 ContentImage를 참조해야 합니다.
        if (result.closing() != null) {
            validateImageId(
                    result.closing().imageId(),
                    imageIds,
                    "closing"
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
