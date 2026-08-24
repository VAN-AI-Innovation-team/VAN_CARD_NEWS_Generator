package com.van.cardnews.domain.generatedimage.controller;

import com.van.cardnews.domain.generatedimage.dto.response.GeneratedCardImageResponse;
import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import com.van.cardnews.domain.generatedimage.repository.GeneratedCardImageRepository;
import com.van.cardnews.domain.generatedimage.service.CardImageGenerationService;
import com.van.cardnews.domain.generatedimage.service.GeneratedImageZipService;
import com.van.cardnews.domain.approval.entity.ApprovalStatus;
import com.van.cardnews.domain.approval.repository.ApprovalRequestRepository;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contents/{contentId}/generated-images")
@RequiredArgsConstructor
public class GeneratedCardImageController {

    private final CardImageGenerationService cardImageGenerationService;
    private final GeneratedCardImageRepository generatedCardImageRepository;
    private final GeneratedImageZipService generatedImageZipService;
    private final ApprovalRequestRepository approvalRequestRepository;

    /** 카드뉴스 이미지 생성 트리거 (Higgsfield 호출 → 저장) */
    @PostMapping("/generate")
    public ResponseEntity<List<GeneratedCardImageResponse>> generate(
            @PathVariable Long contentId
    ) {
        List<GeneratedCardImage> images = cardImageGenerationService.generate(contentId);
        return ResponseEntity.ok(GeneratedCardImageResponse.from(images));
    }

    /** 미리보기용 — 이미 생성된 결과 목록 조회 (CN-008) */
    @GetMapping
    public ResponseEntity<List<GeneratedCardImageResponse>> list(@PathVariable Long contentId) {
        List<GeneratedCardImage> images =
                generatedCardImageRepository.findByContent_IdOrderBySortOrderAsc(contentId);
        return ResponseEntity.ok(GeneratedCardImageResponse.from(images));
    }

    /** 개별 다운로드 (CN-007) */
    @GetMapping("/{imageId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long contentId,
            @PathVariable Long imageId
    ) {
        GeneratedCardImage image = generatedCardImageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("이미지를 찾을 수 없습니다: " + imageId));

        validateApproved(contentId);

        // [확인 필요] storageRef가 로컬 절대경로라는 전제 (LocalImageStorageService 기준)
        Resource resource = new FileSystemResource(image.getStorageRef());

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + image.getCardType().name().toLowerCase()
                                + "-" + image.getCardIndex() + ".png\"")
                .body(resource);
    }

    /** 일괄 다운로드 — ZIP (CN-007) */
    @GetMapping("/download-all")
    public ResponseEntity<byte[]> downloadAll(@PathVariable Long contentId) {
        validateApproved(contentId);

        List<GeneratedCardImage> images =
                generatedCardImageRepository.findByContent_IdOrderBySortOrderAsc(contentId);
        byte[] zip = generatedImageZipService.zip(images);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"content-" + contentId + "-cards.zip\"")
                .body(zip);
    }

    private void validateApproved(Long contentId) {
        boolean approved = approvalRequestRepository
                .findTopByContentIdOrderByRequestedAtDesc(contentId)
                .map(request -> request.getStatus() == ApprovalStatus.APPROVED)
                .orElse(false);

        if (!approved) {
            throw new CustomException(ErrorCode.CONTENT_NOT_APPROVED);
        }
    }
}
