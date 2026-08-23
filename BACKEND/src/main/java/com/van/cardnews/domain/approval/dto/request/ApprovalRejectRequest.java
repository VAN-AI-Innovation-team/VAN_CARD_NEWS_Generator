package com.van.cardnews.domain.approval.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ApprovalRejectRequest(
        @NotBlank(message = "반려 사유는 필수입니다.")
        String reason
) {}
