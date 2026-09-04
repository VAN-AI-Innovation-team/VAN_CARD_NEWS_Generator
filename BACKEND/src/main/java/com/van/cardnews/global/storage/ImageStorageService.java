package com.van.cardnews.global.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {

    /**
     * 원본 이미지 파일을 저장하고 접근 가능한 URL 또는 경로를 반환합니다.
     */
    String store(MultipartFile file);

    /**
     * 저장된 이미지의 바이트를 공개 URL로 읽습니다. (업로드 이미지 / 생성 이미지 모두)
     */
    byte[] read(String imageUrl);

    /**
     * 저장된 이미지의 바이트를 스토리지 참조(storageRef)로 읽습니다.
     * 로컬 구현은 절대 경로, GCS 구현은 {@code gs://버킷/객체} 를 참조로 씁니다.
     */
    byte[] readRef(String storageRef);

    /**
     * 저장된 이미지의 MIME 타입을 반환합니다.
     */
    String getContentType(String imageUrl);

    /**
     * 생성된 카드 이미지 바이트 배열을 저장 후 DB(generated_card_images.image_url)에 기록할 접근 URL과 스토리지 참조를 반환한다.
     */
    StoredImage save(byte[] imageBytes, String fileName);

    /**
     * 저장된 이미지의 공개 URL과 스토리지 내부 참조 정보를 담는 레코드
     */
    record StoredImage(String publicUrl, String storageRef) {}
}
