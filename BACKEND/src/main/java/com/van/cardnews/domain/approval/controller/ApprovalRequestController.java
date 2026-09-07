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

    /**
     * 콘텐츠 승인 요청 생성
     *
     * POST /api/contents/{contentId}/approval-requests
     */
    @PostMapping("/contents/{contentId}/approval-requests")
    public ResponseEntity<ApprovalRequestResponse> requestApproval(
            @PathVariable Long contentId
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        approvalRequestService.requestApproval(contentId)
                );
    }

    /**
     * 현재 승인 대기 중인 요청 목록 조회
     *
     * GET /api/approval-requests
     */
    @GetMapping("/approval-requests")
    public ResponseEntity<List<ApprovalRequestListResponse>>
    getPendingRequests() {

        return ResponseEntity.ok(
                approvalRequestService.getPendingRequests()
        );
    }

    /**
     * 승인 요청 단건 조회
     *
     * GET /api/approval-requests/{approvalRequestId}
     */
    @GetMapping("/approval-requests/{approvalRequestId}")
    public ResponseEntity<ApprovalRequestResponse> getRequest(
            @PathVariable Long approvalRequestId
    ) {
        return ResponseEntity.ok(
                approvalRequestService.getRequest(
                        approvalRequestId
                )
        );
    }

    /**
     * 특정 콘텐츠의 승인 이력 조회
     *
     * GET /api/contents/{contentId}/approval-requests
     */
    @GetMapping("/contents/{contentId}/approval-requests")
    public ResponseEntity<List<ApprovalRequestListResponse>>
    getHistory(
            @PathVariable Long contentId
    ) {
        return ResponseEntity.ok(
                approvalRequestService.getHistory(contentId)
        );
    }

    /**
     * 승인 요청 승인 처리
     *
     * POST /api/approval-requests/{approvalRequestId}/approve
     */
    @PostMapping(
            "/approval-requests/{approvalRequestId}/approve"
    )
    public ResponseEntity<ApprovalRequestResponse> approve(
            @PathVariable Long approvalRequestId
    ) {
        return ResponseEntity.ok(
                approvalRequestService.approve(
                        approvalRequestId
                )
        );
    }

    /**
     * 승인 요청 반려 처리
     *
     * POST /api/approval-requests/{approvalRequestId}/reject
     */
    @PostMapping(
            "/approval-requests/{approvalRequestId}/reject"
    )
    public ResponseEntity<ApprovalRequestResponse> reject(
            @PathVariable Long approvalRequestId,
            @Valid @RequestBody ApprovalRejectRequest request
    ) {
        return ResponseEntity.ok(
                approvalRequestService.reject(
                        approvalRequestId,
                        request
                )
        );
    }
}
