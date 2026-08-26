package com.van.cardnews.global.ai.claude;

import com.van.cardnews.domain.generation.dto.request.CardGenerationRequest;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("dev")
public class MockClaudeClient implements ClaudeClient {

    @Override
    public CardGenerationResult generateCardContent(CardGenerationRequest request) {
        List<CardGenerationRequest.InputImage> images = request.images();
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("Mock 테스트를 위해 최소 1개의 이미지가 필요합니다.");
        }

        Long firstImageId = images.get(0).imageId();
        Long secondImageId = images.size() > 1 ? images.get(1).imageId() : firstImageId;
        CardGenerationResult.CropArea crop = new CardGenerationResult.CropArea(0, 0, 100, 100);
        boolean event = "event".equalsIgnoreCase(request.contentType());
        String date = event ? "xxxx.xx.xx" : null;
        String location = event ? "장소를 넣어주세요" : null;

        return new CardGenerationResult(
                new CardGenerationResult.CoverContent(request.title(), "핵심 안내", date, location, firstImageId, crop),
                List.of(new CardGenerationResult.ContentCard("주요 내용", request.body(), "지금 확인하세요", date, location, secondImageId, crop)),
                new CardGenerationResult.ClosingContent("지금 확인하기", firstImageId, crop)
        );
    }

    @Override
    public CardGenerationResult regenerateCardContent(
            CardGenerationRequest request, CardGenerationResult current,
            String cardType, int cardIndex, String instruction
    ) {
        String suffix = " (AI 재작성)";
        CardGenerationResult.CoverContent cover = current.cover();
        List<CardGenerationResult.ContentCard> content = new ArrayList<>(current.content());
        CardGenerationResult.ClosingContent closing = current.closing();

        if ("cover".equals(cardType)) {
            cover = new CardGenerationResult.CoverContent(
                    current.cover().title() + suffix, current.cover().highlight(),
                    current.cover().date(), current.cover().location(), current.cover().imageId(), current.cover().cropArea());
        } else if ("content".equals(cardType)) {
            CardGenerationResult.ContentCard old = content.get(cardIndex);
            content.set(cardIndex, new CardGenerationResult.ContentCard(
                    old.title() + suffix, old.body(), old.highlight(), old.date(), old.location(), old.imageId(), old.cropArea()));
        } else {
            closing = new CardGenerationResult.ClosingContent(
                    current.closing().cta() + suffix, current.closing().imageId(), current.closing().cropArea());
        }

        return new CardGenerationResult(cover, content, closing);
    }
}
