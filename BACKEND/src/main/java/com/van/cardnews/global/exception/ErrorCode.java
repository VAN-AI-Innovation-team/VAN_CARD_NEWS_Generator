package com.van.cardnews.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT(
            HttpStatus.BAD_REQUEST,
            "입력값이 올바르지 않습니다."
    ),

    TOO_MANY_IMAGES(
            HttpStatus.BAD_REQUEST,
            "이미지는 최대 10장까지 업로드할 수 있습니다."
    ),

    INVALID_IMAGE_FILE(
            HttpStatus.BAD_REQUEST,
            "빈 이미지 파일은 업로드할 수 없습니다."
    ),

    IMAGE_TOO_LARGE(
            HttpStatus.BAD_REQUEST,
            "이미지 파일은 10MB를 초과할 수 없습니다."
    ),

    UNSUPPORTED_IMAGE_TYPE(
            HttpStatus.BAD_REQUEST,
            "지원하지 않는 이미지 형식입니다. (jpg, png, webp만 가능)"
    ),

    IMAGE_UPLOAD_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "이미지 저장 중 오류가 발생했습니다."
    ),

    CONTENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "요청한 콘텐츠를 찾을 수 없습니다."
    ),

    JOB_HISTORY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "요청한 작업 이력을 찾을 수 없습니다."
    ),

    PIPELINE_TRIGGER_FAILED(
            HttpStatus.BAD_GATEWAY,
            "생성 파이프라인 호출에 실패했습니다."
    ),

    TEMPLATE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "요청한 템플릿을 찾을 수 없습니다."
    ),

    TEMPLATE_CODE_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "이미 존재하는 템플릿 코드입니다."
    ),

    TEMPLATE_ALREADY_INACTIVE(
            HttpStatus.CONFLICT,
            "이미 비활성화된 템플릿입니다."
    ),

    INVALID_TEMPLATE_CONTENT_TYPE(
            HttpStatus.BAD_REQUEST,
            "지원하지 않는 콘텐츠 유형입니다."
    ),

    CONTENT_GENERATION_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "현재 콘텐츠의 생성 작업이 진행 중입니다."
    ),

    CONTENT_NOT_READY_FOR_APPROVAL(
            HttpStatus.BAD_REQUEST,
            "카드뉴스 생성이 완료되지 않아 승인 요청할 수 없습니다."
    ),

    APPROVAL_REQUEST_ALREADY_PENDING(
            HttpStatus.CONFLICT,
            "이미 승인 대기 중인 작업물입니다."
    ),

    APPROVAL_REQUEST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "요청한 승인 작업을 찾을 수 없습니다."
    ),

    APPROVAL_REQUEST_NOT_PENDING(
            HttpStatus.CONFLICT,
            "승인 대기 중인 작업만 처리할 수 있습니다."
    ),

    APPROVAL_REJECTION_REASON_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "반려 사유는 필수입니다."
    ),

    DOWNLOAD_HISTORY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "요청한 다운로드 이력을 찾을 수 없습니다."
    ),

    INVALID_DOWNLOAD_CHANNEL(
            HttpStatus.BAD_REQUEST,
            "다운로드 채널 값이 올바르지 않습니다."
    ),

    CONTENT_NOT_APPROVED(
            HttpStatus.FORBIDDEN,
            "승인 완료된 작업물만 다운로드할 수 있습니다."
    ),

    INSTAGRAM_TOKEN_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "저장된 인스타그램 액세스 토큰이 없습니다."
    ),

    INSTAGRAM_TOKEN_REFRESH_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "인스타그램 액세스 토큰 갱신에 실패했습니다."
    ),

    CONTENT_NOT_APPROVED_FOR_PUBLISH(
            HttpStatus.FORBIDDEN,
            "승인 완료된 콘텐츠만 발행할 수 있습니다."
    ),

    PUBLISH_ALREADY_REQUESTED(
            HttpStatus.CONFLICT,
            "이미 진행 중인 발행 건이 있습니다."
    ),

    CONTENT_ALREADY_PUBLISHED(
            HttpStatus.CONFLICT,
            "이미 발행된 콘텐츠입니다."
    ),

    CARD_IMAGES_NOT_READY(
            HttpStatus.BAD_REQUEST,
            "발행할 카드 이미지가 없습니다. 카드 이미지를 먼저 생성해 주세요."
    ),

    PUBLISH_RECORD_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "발행 이력을 찾을 수 없습니다."
    ),

    INVALID_SCHEDULE_TIME(
            HttpStatus.BAD_REQUEST,
            "예약 시각이 올바르지 않습니다."
    ),

    PUBLISH_ALREADY_PROCESSING(
            HttpStatus.CONFLICT,
            "발행이 이미 진행 중이라 취소할 수 없습니다."
    );

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(
            HttpStatus status,
            String defaultMessage
    ) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
