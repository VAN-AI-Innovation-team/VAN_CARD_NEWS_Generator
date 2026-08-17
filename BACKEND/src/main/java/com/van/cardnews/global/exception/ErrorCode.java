package com.van.cardnews.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    TOO_MANY_IMAGES(HttpStatus.BAD_REQUEST, "이미지는 최대 10장까지 업로드할 수 있습니다."),
    INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "빈 이미지 파일은 업로드할 수 없습니다."),
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지 파일은 10MB를 초과할 수 없습니다."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다. (jpg, png, webp만 가능)"),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 저장 중 오류가 발생했습니다."),
    CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 콘텐츠를 찾을 수 없습니다."),
    PIPELINE_TRIGGER_FAILED(HttpStatus.BAD_GATEWAY, "생성 파이프라인 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
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
