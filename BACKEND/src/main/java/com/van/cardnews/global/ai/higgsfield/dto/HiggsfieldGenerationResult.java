package com.van.cardnews.global.ai.higgsfield.dto;

import java.util.List;

public record HiggsfieldGenerationResult(
        List<GeneratedCard> cards
) {
    /**
     * 크기 필드를 두지 않는다. 구현이 보고하는 크기는 실제 이미지와 어긋날 수 있고
     * (prod는 템플릿 캔버스 값을 그대로 돌려주는데 실제 응답은 2K다), 크기의 진실은
     * 언제나 imageBytes 안에 있다. 저장 직전에 바이트에서 읽는다.
     */
    public record GeneratedCard(
            CardType cardType,
            int cardIndex,     // content 카드 내 순서. cover/closing은 0
            byte[] imageBytes  // [확인 필요] Higgsfield 실제 응답이 바이트인지 URL인지 확인 필요 — Mock은 바이트로 가정
    ) {
        public enum CardType { COVER, CONTENT, CLOSING }
    }
}
