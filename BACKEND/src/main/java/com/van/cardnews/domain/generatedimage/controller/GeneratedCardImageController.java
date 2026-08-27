package com.van.cardnews.domain.generatedimage.controller;

import com.van.cardnews.domain.generatedimage.dto.response.GeneratedCardImageResponse;
import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import com.van.cardnews.domain.generatedimage.repository.GeneratedCardImageRepository;
import com.van.cardnews.domain.generatedimage.service.CardImageGenerationService;
import com.van.cardnews.domain.generatedimage.service.GeneratedImageZipService;
import com.van.cardnews.domain.approval.entity.ApprovalStatus;
import com.van.cardnews.domain.approval.repository.ApprovalRequestRepository;
import com.van.cardnews.domain.download.entity.DownloadType;
import com.van.cardnews.domain.download.service.DownloadHistoryService;
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
    private final DownloadHistoryService downloadHistoryService;

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
    public ResponseEntity<List<GeneratedCardImageResponse>> list(
            @PathVariable Long contentId
    ) {
        List<GeneratedCardImage> images =
                generatedCardImageRepository.findByContent_IdOrderBySortOrderAsc(contentId);

        return ResponseEntity.ok(GeneratedCardImageResponse.from(images));
    }

    /** 개별 다운로드 */
    @GetMapping("/{imageId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long contentId,
            @PathVariable Long imageId,
            @RequestParam(defaultValue = "WEB") String channel,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "SYSTEM") String actorId
    ) {
        // 승인된 콘텐츠만 다운로드할 수 있습니다.
        validateApproved(contentId);

        Long historyId = downloadHistoryService.start(
                contentId,
                normalizeChannel(channel),
                DownloadType.SINGLE,
                imageId,
                actorId
        );

        try {
            GeneratedCardImage image = generatedCardImageRepository
                    .findByIdAndContent_Id(imageId, contentId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "이미지를 찾을 수 없습니다: " + imageId
                    ));

            if (image.getStorageRef() == null || image.getStorageRef().isBlank()) {
                throw new IllegalStateException("다운로드할 이미지 저장 경로가 없습니다.");
            }

            Resource resource = new FileSystemResource(image.getStorageRef());

            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalStateException("다운로드할 이미지 파일을 찾을 수 없습니다.");
            }

            downloadHistoryService.markSuccess(historyId);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" +
                                    image.getCardType().name().toLowerCase() +
                                    "-" +
                                    image.getCardIndex() +
                                    ".png\""
                    )
                    .body(resource);

        } catch (Exception e) {
            downloadHistoryService.markFailed(
                    historyId,
                    e.getMessage() != null
                            ? e.getMessage()
                            : "개별 다운로드에 실패했습니다."
            );
            throw e;
        }
    }

    /** 일괄 다운로드 — ZIP (CN-007) */
    @GetMapping("/download-all")
    public ResponseEntity<byte[]> downloadAll(
            @PathVariable Long contentId,
            @RequestParam(defaultValue = "WEB") String channel,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "SYSTEM") String actorId
    ) {
        // 승인된 콘텐츠만 다운로드할 수 있습니다.
        validateApproved(contentId);

        Long historyId = downloadHistoryService.start(
                contentId,
                normalizeChannel(channel),
                DownloadType.ZIP,
                null,
                actorId
        );

        try {
            List<GeneratedCardImage> images =
                    generatedCardImageRepository.findByContent_IdOrderBySortOrderAsc(contentId);

            if (images.isEmpty()) {
                throw new IllegalStateException("다운로드할 카드 이미지가 없습니다.");
            }

            byte[] zip = generatedImageZipService.zip(images);

            downloadHistoryService.markSuccess(historyId);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"content-" +
                                    contentId +
                                    "-cards.zip\""
                    )
                    .body(zip);

        } catch (Exception e) {
            downloadHistoryService.markFailed(
                    historyId,
                    e.getMessage() != null
                            ? e.getMessage()
                            : "일괄 다운로드에 실패했습니다."
            );
            throw e;
        }
    }

    private String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return "WEB";
        }

        String normalized = channel.trim().toUpperCase();

        if (normalized.length() > 50) {
            throw new CustomException(ErrorCode.INVALID_DOWNLOAD_CHANNEL);
        }

        return normalized;
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
