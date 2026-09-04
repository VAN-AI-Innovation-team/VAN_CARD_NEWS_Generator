package com.van.cardnews.global.storage;

import com.google.cloud.storage.BlobId;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스토리지 참조/공개 URL 해석 규칙 검증.
 * 로컬·GCS 두 구현이 같은 규칙(업로드 프리픽스 vs 생성 프리픽스)을 따라야 한다.
 */
class ImageStorageServiceTest {

    private static final String UPLOAD_BASE = "https://example.test/uploads";
    private static final String GENERATED_BASE = "https://example.test/generated";

    @Test
    void save_후_readRef로_같은_바이트를_돌려준다(@TempDir Path tempDir) {
        LocalImageStorageService storage = localStorage(tempDir);
        byte[] bytes = {1, 2, 3, 4};

        ImageStorageService.StoredImage stored = storage.save(bytes, "card.png");

        assertThat(stored.publicUrl()).isEqualTo(GENERATED_BASE + "/card.png");
        assertThat(storage.readRef(stored.storageRef())).isEqualTo(bytes);
    }

    @Test
    void 생성_이미지_공개URL은_생성_디렉터리에서_읽는다(@TempDir Path tempDir) throws Exception {
        LocalImageStorageService storage = localStorage(tempDir);
        byte[] uploaded = {9, 9};
        Files.write(tempDir.resolve("uploads/same-name.png"), uploaded);

        byte[] generated = {7, 7};
        storage.save(generated, "same-name.png");

        // 같은 파일명이 양쪽에 있을 때 base URL로 구분되지 않으면 업로드본을 잘못 읽는다.
        assertThat(storage.read(GENERATED_BASE + "/same-name.png")).isEqualTo(generated);
        assertThat(storage.read(UPLOAD_BASE + "/same-name.png")).isEqualTo(uploaded);
    }

    @Test
    void 경로_탈출_시도는_거부한다(@TempDir Path tempDir) {
        LocalImageStorageService storage = localStorage(tempDir);

        assertThatThrownBy(() -> storage.read(UPLOAD_BASE + "/.."))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    void gs_참조를_버킷과_객체명으로_분해한다() {
        BlobId blobId = GcsImageStorageService.parseBlobId("gs://van-cards/generated/card.png");

        assertThat(blobId.getBucket()).isEqualTo("van-cards");
        assertThat(blobId.getName()).isEqualTo("generated/card.png");
    }

    @Test
    void gs_형식이_아닌_참조는_거부한다() {
        assertThatThrownBy(() -> GcsImageStorageService.parseBlobId("/app/generated/card.png"))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> GcsImageStorageService.parseBlobId("gs://van-cards"))
                .isInstanceOf(IllegalStateException.class);
    }

    private LocalImageStorageService localStorage(Path tempDir) {
        return new LocalImageStorageService(
                tempDir.resolve("uploads").toString(),
                UPLOAD_BASE,
                tempDir.resolve("generated").toString(),
                GENERATED_BASE
        );
    }
}
