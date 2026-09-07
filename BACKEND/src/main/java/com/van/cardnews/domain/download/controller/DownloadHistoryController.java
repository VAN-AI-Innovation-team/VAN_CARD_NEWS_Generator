package com.van.cardnews.domain.download.controller;

import com.van.cardnews.domain.download.dto.response.DownloadHistoryResponse;
import com.van.cardnews.domain.download.service.DownloadHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contents/{contentId}/download-history")
@RequiredArgsConstructor
public class DownloadHistoryController {

    private final DownloadHistoryService downloadHistoryService;

    /**
     * 콘텐츠의 다운로드 이력을 조회합니다.
     *
     * channel을 지정하면 해당 채널의 이력만 조회합니다.
     */
    @GetMapping
    public ResponseEntity<List<DownloadHistoryResponse>> getHistory(
            @PathVariable Long contentId,
            @RequestParam(required = false) String channel
    ) {
        return ResponseEntity.ok(
                downloadHistoryService.getHistory(contentId, channel)
        );
    }
}
