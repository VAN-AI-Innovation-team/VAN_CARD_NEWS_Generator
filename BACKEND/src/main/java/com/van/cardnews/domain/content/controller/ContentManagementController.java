package com.van.cardnews.domain.content.controller;

import com.van.cardnews.domain.content.dto.response.ContentManagementListResponse;
import com.van.cardnews.domain.content.service.ContentManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
