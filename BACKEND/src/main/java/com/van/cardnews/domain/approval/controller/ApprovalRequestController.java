package com.van.cardnews.domain.approval.controller;

import com.van.cardnews.domain.approval.dto.response.ApprovalRequestResponse;
import com.van.cardnews.domain.approval.service.ApprovalRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contents/{contentId}/approval-requests")
@RequiredArgsConstructor
public class ApprovalRequestController {

    private final ApprovalRequestService approvalRequestService;

    @PostMapping
    public ResponseEntity<ApprovalRequestResponse> requestApproval(
            @PathVariable Long contentId
    ) {
        ApprovalRequestResponse response =
                approvalRequestService.requestApproval(contentId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
