package com.van.cardnews.global.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {

    /**
     * 이미지 파일을 저장하고 접근 가능한 URL(또는 경로)을 반환합니다.
     */
    String store(MultipartFile file);
}
