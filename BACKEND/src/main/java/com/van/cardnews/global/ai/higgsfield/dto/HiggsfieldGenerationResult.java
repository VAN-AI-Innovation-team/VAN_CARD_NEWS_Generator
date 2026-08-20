package com.van.cardnews.global.ai.higgsfield.dto;

import java.util.List;

public record HiggsfieldGenerationResult(
        List<GeneratedCard> cards
) {
    public record GeneratedCard(
            CardType cardType,
            int cardIndex,     // content 카드 내 순서. cover/closing은 0
            byte[] imageBytes, // [확인 필요] Higgsfield 실제 응답이 바이트인지 URL인지 확인 필요 — Mock은 바이트로 가정
            int width,
            int height
    ) {
        public enum CardType { COVER, CONTENT, CLOSING }
    }
}
