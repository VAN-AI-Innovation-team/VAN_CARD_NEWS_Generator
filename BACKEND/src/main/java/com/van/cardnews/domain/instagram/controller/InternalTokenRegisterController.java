package com.van.cardnews.domain.instagram.controller;

import com.van.cardnews.domain.instagram.dto.request.InstagramTokenRegisterRequest;
import com.van.cardnews.domain.instagram.dto.response.InstagramTokenRegisterResponse;
import com.van.cardnews.domain.instagram.service.InstagramTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수동 OAuth로 받아온 장기 토큰을 넣는 통로입니다.
 *
 * 토큰은 AES/GCM 암호문으로만 저장되므로 DB에 직접 넣을 수 없고, 쓰는 코드는 dev 시더뿐이었습니다.
 * 그래서 실 토큰을 확보해도 주입할 방법이 없었습니다.
 *
 * 인증은 {@code /internal/**} 공유 시크릿 헤더를 그대로 씁니다(WebConfig). 호출자가 사람으로
 * 바뀌었을 뿐 노출 범위는 같으므로 인증 축을 새로 만들 이유가 없습니다.
 *
 * 동의 화면부터 코드 교환까지 앱이 직접 하는 정식 OAuth 콜백은 범위 밖입니다. 그 흐름이
 * 생기면 이 엔드포인트가 그대로 저장 계층이 됩니다.
 */
@RestController
@RequiredArgsConstructor
public class InternalTokenRegisterController {

    private final InstagramTokenService instagramTokenService;

    @PostMapping("/internal/instagram/token")
    public ResponseEntity<InstagramTokenRegisterResponse> registerToken(
            @Valid @RequestBody InstagramTokenRegisterRequest request
    ) {
        return ResponseEntity.ok(instagramTokenService.register(request));
    }
}
