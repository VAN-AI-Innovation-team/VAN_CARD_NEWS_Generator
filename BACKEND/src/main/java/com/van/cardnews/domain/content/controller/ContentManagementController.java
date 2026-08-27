package com.van.cardnews.domain.content.controller;

import com.van.cardnews.domain.content.dto.response.ContentManagementListResponse;
import com.van.cardnews.domain.content.dto.response.ContentHistoryPageResponse;
import com.van.cardnews.domain.content.service.ContentManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;

import java.util.List;

@RestController
@RequestMapping("/api/contents")
@RequiredArgsConstructor
public class ContentManagementController {

    private final ContentManagementService contentManagementService;

    @GetMapping
    public ResponseEntity<List<ContentManagementListResponse>> getContents() {
        return ResponseEntity.ok(contentManagementService.getContents());
    }

    /**
     * 과거 생성된 콘텐츠 이력을 페이징하여 조회합니다.
     * 예: GET /api/contents/history?page=0&size=20
     */
    @GetMapping("/history")
    public ResponseEntity<ContentHistoryPageResponse> getContentHistory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(contentManagementService.getContentHistory(pageable));
    }
}
