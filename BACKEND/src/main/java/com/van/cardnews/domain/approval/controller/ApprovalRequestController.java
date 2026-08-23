package com.van.cardnews.domain.approval.controller;

import com.van.cardnews.domain.approval.dto.request.ApprovalRejectRequest;
import com.van.cardnews.domain.approval.dto.response.ApprovalRequestListResponse;
import com.van.cardnews.domain.approval.dto.response.ApprovalRequestResponse;
import com.van.cardnews.domain.approval.service.ApprovalRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApprovalRequestController {

    private final ApprovalRequestService approvalRequestService;

    @PostMapping("/contents/{contentId}/approval-requests")
    public ResponseEntity<ApprovalRequestResponse> requestApproval(
            @PathVariable Long contentId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(approvalRequestService.requestApproval(contentId));
    }

    @GetMapping("/approval-requests")
    public ResponseEntity<List<ApprovalRequestListResponse>> getPendingRequests() {
        return ResponseEntity.ok(approvalRequestService.getPendingRequests());
    }

    @GetMapping("/approval-requests/{approvalRequestId}")
    public ResponseEntity<ApprovalRequestResponse> getRequest(
            @PathVariable Long approvalRequestId
    ) {
        return ResponseEntity.ok(approvalRequestService.getRequest(approvalRequestId));
    }

    @PostMapping("/approval-requests/{approvalRequestId}/approve")
    public ResponseEntity<ApprovalRequestResponse> approve(
            @PathVariable Long approvalRequestId
    ) {
        return ResponseEntity.ok(approvalRequestService.approve(approvalRequestId));
    }

    @PostMapping("/approval-requests/{approvalRequestId}/reject")
    public ResponseEntity<ApprovalRequestResponse> reject(
            @PathVariable Long approvalRequestId,
            @Valid @RequestBody ApprovalRejectRequest request
    ) {
        return ResponseEntity.ok(
                approvalRequestService.reject(approvalRequestId, request)
        );
    }
}
