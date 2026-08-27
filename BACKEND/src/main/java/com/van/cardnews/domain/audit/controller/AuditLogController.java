package com.van.cardnews.domain.audit.controller;

import com.van.cardnews.domain.audit.dto.response.AuditLogResponse;
import com.van.cardnews.domain.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> getAll() {
        return ResponseEntity.ok(auditLogService.getAll());
    }

    @GetMapping("/contents/{contentId}")
    public ResponseEntity<List<AuditLogResponse>> getByContentId(
            @PathVariable Long contentId
    ) {
        return ResponseEntity.ok(auditLogService.getByContentId(contentId));
    }
}
