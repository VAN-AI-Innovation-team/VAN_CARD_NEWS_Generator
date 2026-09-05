package com.van.cardnews.global.storage;

import com.google.cloud.storage.BlobId;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.Arrays;

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
    void GCS도_공개URL의_프리픽스로_업로드와_생성_이미지를_구분한다() {
        GcsImageStorageService storage = gcsStorage();

        assertThat(storage.resolveObjectName(
                "https://storage.googleapis.com/van-cards/generated/same-name.png"))
                .isEqualTo("generated/same-name.png");

        assertThat(storage.resolveObjectName(
                "https://storage.googleapis.com/van-cards/uploads/same-name.png"))
                .isEqualTo("uploads/same-name.png");
    }

    @Test
    void GCS도_경로_탈출_시도는_거부한다() {
        GcsImageStorageService storage = gcsStorage();

        assertThatThrownBy(() -> storage.resolveObjectName(
                "https://storage.googleapis.com/van-cards/uploads/.."))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    void 버킷_설정이_비어_있으면_기동에_실패한다() {
        assertThatThrownBy(() -> new GcsImageStorageService(null, "", "uploads", "generated"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.gcs.bucket");
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

    /** URL 해석은 GCS 호출을 타지 않으므로 클라이언트 없이 검증한다. */
    private GcsImageStorageService gcsStorage() {
        return new GcsImageStorageService(null, "van-cards", "uploads", "generated");
    }

    /**
     * Spring은 생성자가 둘 이상이면 @Autowired가 붙은 것을 찾고, 없으면 기본 생성자를 찾는다.
     * 이 클래스는 운영용(@Value 주입)과 테스트용(Storage 직접 주입) 생성자를 함께 두므로
     * 표시가 빠지면 컨텍스트 기동이 실패한다. GCS_BUCKET이 설정된 환경에서만 이 빈이
     * 생성되기 때문에 CI 기동 테스트로는 잡히지 않아 여기서 직접 검증한다.
     */
    @Test
    void GCS_구현은_주입할_생성자가_하나로_정해져_있다() {
        Constructor<?>[] constructors =
                GcsImageStorageService.class.getDeclaredConstructors();

        long annotated =
                Arrays.stream(constructors)
                        .filter(c -> c.isAnnotationPresent(Autowired.class))
                        .count();

        assertThat(constructors.length).isGreaterThan(1);
        assertThat(annotated).isEqualTo(1);
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
