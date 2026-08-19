package com.van.cardnews.global.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {

    /**
     * 이미지 파일을 저장하고 접근 가능한 URL 또는 경로를 반환합니다.
     */
    String store(MultipartFile file);

    /**
     * 저장된 원본 이미지의 바이트를 읽습니다.
     */
    byte[] read(String imageUrl);

    /**
     * 저장된 이미지의 MIME 타입을 반환합니다.
     */
    String getContentType(String imageUrl);
}
