package com.van.cardnews.domain.template.controller;

import com.van.cardnews.domain.template.dto.request.TemplateCreateRequest;
import com.van.cardnews.domain.template.dto.request.TemplateUpdateRequest;
import com.van.cardnews.domain.template.dto.response.TemplateListResponse;
import com.van.cardnews.domain.template.dto.response.TemplateResponse;
import com.van.cardnews.domain.template.service.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    /**
     * 활성 템플릿 목록 조회
     *
     * GET /api/templates
     * GET /api/templates?contentType=event
     */
    @GetMapping
    public ResponseEntity<TemplateListResponse> getTemplates(
            @RequestParam(required = false) String contentType
    ) {
        return ResponseEntity.ok(
                templateService.getTemplates(contentType)
        );
    }

    /**
     * 특정 템플릿 버전 조회
     */
    @GetMapping("/{templateId}")
    public ResponseEntity<TemplateResponse> getTemplate(
            @PathVariable Long templateId
    ) {
        return ResponseEntity.ok(
                templateService.getTemplate(templateId)
        );
    }

    /**
     * 신규 템플릿 등록
     *
     * 새 code가 추가되는 경우 사용합니다.
     */
    @PostMapping
    public ResponseEntity<TemplateResponse> createTemplate(
            @Valid @RequestBody TemplateCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        templateService.createTemplate(
                                request
                        )
                );
    }

    /**
     * 기존 템플릿 새 버전 생성
     */
    @PutMapping("/{templateId}")
    public ResponseEntity<TemplateResponse> updateTemplate(
            @PathVariable Long templateId,
            @Valid @RequestBody TemplateUpdateRequest request
    ) {
        return ResponseEntity.ok(
                templateService.updateTemplate(
                        templateId,
                        request
                )
        );
    }

    /**
     * 템플릿 비활성화
     */
    @PatchMapping("/{templateId}/deactivate")
    public ResponseEntity<Void> deactivateTemplate(
            @PathVariable Long templateId
    ) {
        templateService.deactivateTemplate(templateId);

        return ResponseEntity.noContent().build();
    }
}
