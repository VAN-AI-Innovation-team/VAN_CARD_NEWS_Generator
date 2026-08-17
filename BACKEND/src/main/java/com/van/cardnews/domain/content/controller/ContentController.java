package com.van.cardnews.domain.content.controller;

import com.van.cardnews.domain.content.dto.request.ContentCreateRequest;
import com.van.cardnews.domain.content.dto.response.ContentCreateResponse;
import com.van.cardnews.domain.content.service.ContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/contents")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    /**
     * 카드뉴스 생성 요청을 접수합니다.
     *
     * 요청 형식: multipart/form-data
     *  - part "data"   : application/json, {"title": "...", "body": "...", "template": "..."}
     *  - part "images" : 이미지 파일 0~10개 (jpg/png/webp, 각 10MB 이하)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContentCreateResponse> createContent(
            @RequestPart("data") @Valid ContentCreateRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        List<MultipartFile> safeImages = images != null ? images : Collections.emptyList();
        ContentCreateResponse response = contentService.createContent(request, safeImages);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
