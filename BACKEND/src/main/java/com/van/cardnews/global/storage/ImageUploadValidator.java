package com.van.cardnews.global.storage;

import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 업로드 이미지 검증 규칙. 로컬/GCS 구현이 동일한 기준을 쓰도록 한곳에 둔다.
 */
final class ImageUploadValidator {

    static final List<String> ALLOWED_CONTENT_TYPES =
            List.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp"
            );

    private static final long MAX_FILE_SIZE_BYTES =
            10L * 1024 * 1024;

    private ImageUploadValidator() {
    }

    static void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new CustomException(
                    ErrorCode.INVALID_IMAGE_FILE
            );
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new CustomException(
                    ErrorCode.IMAGE_TOO_LARGE
            );
        }

        if (!ALLOWED_CONTENT_TYPES.contains(
                file.getContentType()
        )) {
            throw new CustomException(
                    ErrorCode.UNSUPPORTED_IMAGE_TYPE
            );
        }
    }

    static String extractExtension(
            String originalFilename
    ) {
        if (!StringUtils.hasText(originalFilename) ||
                !originalFilename.contains(".")) {

            return "";
        }

        return originalFilename.substring(
                originalFilename.lastIndexOf('.')
        );
    }

    /**
     * 공개 URL에서 파일명만 안전하게 잘라낸다. 경로 탈출 시도는 거부한다.
     */
    static String extractFileName(String imageUrl) {
        String fileName =
                imageUrl.substring(
                        imageUrl.lastIndexOf('/') + 1
                );

        if (!StringUtils.hasText(fileName) ||
                fileName.contains("..") ||
                fileName.contains("\\") ||
                fileName.contains("/")) {

            throw new CustomException(
                    ErrorCode.INVALID_IMAGE_FILE
            );
        }

        return fileName;
    }
}
