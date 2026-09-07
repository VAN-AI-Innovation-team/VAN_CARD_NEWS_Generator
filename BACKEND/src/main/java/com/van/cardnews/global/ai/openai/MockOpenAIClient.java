package com.van.cardnews.global.ai.openai;

import com.van.cardnews.domain.generation.dto.request.CardGenerationRequest;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Profile("dev")
public class MockOpenAIClient implements OpenAIClient {

    /**
     * 아래 생성 메서드가 지어내는 고정 문구입니다. 사용자 입력에서 온 값(제목·본문)은 여기에 없습니다.
     *
     * 이 목록이 이 클래스 안에 있는 것이 요점입니다. 캡션 조립부는 이 문구들을 알지 못하고,
     * 실구현으로 갈리는 순간 판정도 함께 갈립니다.
     */
    private static final Map<String, Set<String>> PLACEHOLDERS = Map.of(
            "cover.highlight", Set.of("핵심 안내"),
            "cover.date", Set.of("xxxx.xx.xx"),
            "cover.location", Set.of("장소를 넣어주세요"),
            "content.title", Set.of("주요 내용"),
            "content.highlight", Set.of("지금 확인하세요"),
            "content.date", Set.of("xxxx.xx.xx"),
            "content.location", Set.of("장소를 넣어주세요"),
            "closing.cta", Set.of("지금 확인하기")
    );

    @Override
    public boolean isPlaceholder(String fieldPath, String value) {
        return PLACEHOLDERS.getOrDefault(fieldPath, Set.of()).contains(value);
    }

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

        CardGenerationResult.CropArea crop =
                new CardGenerationResult.CropArea(
                        0,
                        0,
                        100,
                        100
                );

        boolean event =
                "event".equalsIgnoreCase(
                        request.contentType()
                );

        String date =
                event
                        ? "xxxx.xx.xx"
                        : null;

        String location =
                event
                        ? "장소를 넣어주세요"
                        : null;

        return new CardGenerationResult(
                new CardGenerationResult.CoverContent(
                        request.title(),
                        "핵심 안내",
                        date,
                        location,
                        firstImageId,
                        crop
                ),

                List.of(
                        new CardGenerationResult.ContentCard(
                                "주요 내용",
                                request.body(),
                                "지금 확인하세요",
                                date,
                                location,
                                secondImageId,
                                crop
                        )
                ),

                new CardGenerationResult.ClosingContent(
                        "지금 확인하기",
                        firstImageId,
                        crop
                )
        );
    }

    @Override
    public CardGenerationResult regenerateCardContent(
            CardGenerationRequest request,
            CardGenerationResult current,
            String cardType,
            int cardIndex,
            String instruction
    ) {
        String suffix = " (AI 재작성)";

        CardGenerationResult.CoverContent cover =
                current.cover();

        List<CardGenerationResult.ContentCard> content =
                new ArrayList<>(current.content());

        CardGenerationResult.ClosingContent closing =
                current.closing();

        if ("cover".equals(cardType)) {

            cover =
                    new CardGenerationResult.CoverContent(
                            current.cover().title() + suffix,
                            current.cover().highlight(),
                            current.cover().date(),
                            current.cover().location(),
                            current.cover().imageId(),
                            current.cover().cropArea()
                    );

        } else if ("content".equals(cardType)) {

            CardGenerationResult.ContentCard old =
                    content.get(cardIndex);

            content.set(
                    cardIndex,
                    new CardGenerationResult.ContentCard(
                            old.title() + suffix,
                            old.body(),
                            old.highlight(),
                            old.date(),
                            old.location(),
                            old.imageId(),
                            old.cropArea()
                    )
            );

        } else {

            closing =
                    new CardGenerationResult.ClosingContent(
                            current.closing().cta() + suffix,
                            current.closing().imageId(),
                            current.closing().cropArea()
                    );
        }

        return new CardGenerationResult(
                cover,
                content,
                closing
        );
    }
}
