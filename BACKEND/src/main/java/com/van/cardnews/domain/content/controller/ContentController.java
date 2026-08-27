package com.van.cardnews.domain.content.controller;

import com.van.cardnews.domain.content.dto.request.ContentCreateRequest;
import com.van.cardnews.domain.content.dto.request.ContentEditRequest;
import com.van.cardnews.domain.content.dto.request.ContentTemplateUpdateRequest;
import com.van.cardnews.domain.content.dto.request.CardRegenerationRequest;
import com.van.cardnews.domain.content.dto.response.ContentCreateResponse;
import com.van.cardnews.domain.content.dto.request.ContentPreviewUpdateRequest;
import com.van.cardnews.domain.content.dto.request.HighlightUpdateRequest;
import com.van.cardnews.domain.content.dto.response.ContentPreviewResponse;
import com.van.cardnews.domain.content.service.ContentService;
import com.van.cardnews.domain.content.dto.request.CardImagePlacementUpdateRequest;
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
     * 기존 콘텐츠를 동일 입력값으로 복제합니다.
     * regenerate=true이면 복제 직후 전체 카드뉴스 생성 파이프라인을 실행합니다.
     */
    @PostMapping("/{contentId}/clone")
    public ResponseEntity<ContentCreateResponse> cloneContent(
            @PathVariable Long contentId,
            @RequestParam(defaultValue = "false") boolean regenerate
    ) {
        ContentCreateResponse response = contentService.cloneContent(contentId, regenerate);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 카드뉴스 생성 결과를 조회합니다.
     *
     * 생성 파이프라인이 비동기로 실행되므로
     * cardGenerationResult가 아직 null일 수 있습니다.
     * 프론트엔드는 이 응답을 polling하여 생성 완료 여부를 확인합니다.
     */
    @GetMapping("/{contentId}/preview")
    public ResponseEntity<ContentPreviewResponse> getPreview(
            @PathVariable Long contentId
    ) {
        ContentPreviewResponse response =
                contentService.getPreview(contentId);

        return ResponseEntity.ok(response);
    }

    /**
     * Claude가 생성한 카드 구성 결과를 사용자가 수정한 내용으로 갱신합니다.
     *
     * 실제 카드 이미지 생성(Higgsfield)은 이 API에서 실행하지 않습니다.
     * 수정된 CardGenerationResult를 저장하고,
     * 최종 생성 파이프라인에서 해당 결과를 사용합니다.
     */
    @PutMapping(value = "/{contentId}/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContentCreateResponse> editContent(
            @PathVariable Long contentId,
            @RequestPart("data") @Valid ContentEditRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        List<MultipartFile> safeImages = images != null ? images : Collections.emptyList();
        ContentCreateResponse response = contentService.editContent(contentId, request, safeImages);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{contentId}/template")
    public ResponseEntity<ContentPreviewResponse> updateTemplate(
            @PathVariable Long contentId,
            @Valid @RequestBody ContentTemplateUpdateRequest request
    ) {
        return ResponseEntity.ok(
                contentService.updateTemplate(contentId, request)
        );
    }

    @PostMapping("/{contentId}/cards/regenerate")
    public ResponseEntity<ContentPreviewResponse> regenerateCard(
            @PathVariable Long contentId,
            @Valid @RequestBody CardRegenerationRequest request
    ) {
        return ResponseEntity.ok(
                contentService.regenerateCard(contentId, request)
        );
    }

    @PutMapping("/{contentId}/highlight")
    public ResponseEntity<ContentPreviewResponse> updateHighlight(
            @PathVariable Long contentId,
            @Valid @RequestBody HighlightUpdateRequest request
    ) {
        ContentPreviewResponse response =
                contentService.updateHighlight(contentId, request);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{contentId}/preview")
    public ResponseEntity<ContentPreviewResponse> updatePreview(
            @PathVariable Long contentId,
            @Valid @RequestBody ContentPreviewUpdateRequest request
    ) {
        ContentPreviewResponse response =
                contentService.updatePreview(
                        contentId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{contentId}/images/crop")
    public ResponseEntity<ContentPreviewResponse> updateImageCrops(
            @PathVariable Long contentId,
            @Valid @RequestBody CardImagePlacementUpdateRequest request
    ) {
        ContentPreviewResponse response =
                contentService.updateImageCrops(
                        contentId,
                        request
                );

        return ResponseEntity.ok(response);
    }

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
