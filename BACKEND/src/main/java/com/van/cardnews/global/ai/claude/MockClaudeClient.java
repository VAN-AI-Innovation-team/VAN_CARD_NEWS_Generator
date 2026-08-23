package com.van.cardnews.global.ai.claude;

import com.van.cardnews.domain.generation.dto.request.CardGenerationRequest;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("dev")
public class MockClaudeClient implements ClaudeClient {

    @Override
    public CardGenerationResult generateCardContent(
            CardGenerationRequest request
    ) {
        List<CardGenerationRequest.InputImage> images =
                request.images();

        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException(
                    "Mock 테스트를 위해 최소 1개의 이미지가 필요합니다."
            );
        }

        Long firstImageId =
                images.get(0).imageId();

        Long secondImageId =
                images.size() > 1
                        ? images.get(1).imageId()
                        : firstImageId;

        CardGenerationResult.CropArea defaultCropArea =
                new CardGenerationResult.CropArea(0.0, 0.0, 100.0, 100.0);

        boolean isEvent = "event".equalsIgnoreCase(request.contentType());

        String date = isEvent ? "xxxx.xx.xx" : null;
        String location = isEvent ? "장소를 넣어주세요" : null;

        return new CardGenerationResult(
                new CardGenerationResult.CoverContent(
                        request.title(),
                        "핵심 안내",
                        date,
                        location,
                        firstImageId,
                        defaultCropArea
                ),
                List.of(
                        new CardGenerationResult.ContentCard(
                                "주요 내용",
                                request.body(),
                                "지금 확인하세요",
                                date,
                                location,
                                secondImageId,
                                defaultCropArea
                        )
                ),
                new CardGenerationResult.ClosingContent(
                        "지금 확인하기",
                        firstImageId,
                        defaultCropArea
                )
        );
    }
}
