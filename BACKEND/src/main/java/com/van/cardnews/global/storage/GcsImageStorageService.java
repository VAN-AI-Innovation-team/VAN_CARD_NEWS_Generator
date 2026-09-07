package com.van.cardnews.global.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import com.van.cardnews.global.image.ImageBytes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 이미지를 GCS 버킷에 저장한다.
 *
 * Cloud Run 인스턴스는 언제든 교체되므로 컨테이너 로컬 디스크에 저장한 이미지의 URL은
 * 곧 404가 된다. Meta가 발행 시점에 그 URL을 직접 가져가기 때문에 배포 환경에서는
 * 반드시 인스턴스 수명과 무관한 스토리지를 써야 한다.
 *
 * 활성화 조건은 프로필이 아니라 {@code app.gcs.bucket} 설정 여부다. 프로필 축에는
 * OpenAI·Higgsfield 클라이언트도 묶여 있어, 스토리지 하나를 바꾸려고 프로필을 뒤집으면
 * 무관한 외부 연동까지 함께 실구현으로 바뀌기 때문이다.
 *
 * 버킷은 공개 읽기(allUsers: Storage Object Viewer)로 두고, 인증은 Cloud Run
 * 서비스 계정의 Application Default Credentials를 사용한다.
 */
@Slf4j
@Service
@ConditionalOnExpression("'${app.gcs.bucket:}'.length() > 0")
public class GcsImageStorageService implements ImageStorageService {

    private static final String PUBLIC_BASE = "https://storage.googleapis.com/";

    private final Storage storage;
    private final String bucket;
    private final String uploadPrefix;
    private final String generatedPrefix;

    @Autowired
    public GcsImageStorageService(
            @Value("${app.gcs.bucket}") String bucket,
            @Value("${app.gcs.upload-prefix}") String uploadPrefix,
            @Value("${app.gcs.generated-prefix}") String generatedPrefix
    ) {
        this(
                StorageOptions.getDefaultInstance().getService(),
                bucket,
                uploadPrefix,
                generatedPrefix
        );
    }

    GcsImageStorageService(
            Storage storage,
            String bucket,
            String uploadPrefix,
            String generatedPrefix
    ) {
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalStateException("app.gcs.bucket 설정이 필요합니다.");
        }

        this.storage = storage;
        this.bucket = bucket;
        this.uploadPrefix = uploadPrefix;
        this.generatedPrefix = generatedPrefix;
    }

    @Override
    public String store(MultipartFile file) {
        ImageUploadValidator.validate(file);

        String objectName =
                uploadPrefix + "/" + UUID.randomUUID()
                        + ImageUploadValidator.extractExtension(file.getOriginalFilename());

        try {
            upload(objectName, file.getBytes(), file.getContentType());

        } catch (IOException | StorageException e) {
            log.error("이미지 저장 실패: {}", objectName, e);

            throw new CustomException(
                    ErrorCode.IMAGE_UPLOAD_FAILED
            );
        }

        return publicUrl(objectName);
    }

    @Override
    public byte[] read(String imageUrl) {
        try {
            return storage.readAllBytes(
                    BlobId.of(bucket, resolveObjectName(imageUrl))
            );

        } catch (StorageException e) {
            log.error("이미지 읽기 실패: {}", imageUrl, e);

            throw new CustomException(
                    ErrorCode.IMAGE_UPLOAD_FAILED
            );
        }
    }

    @Override
    public byte[] readRef(String storageRef) {
        try {
            return storage.readAllBytes(parseBlobId(storageRef));

        } catch (StorageException e) {
            log.error("이미지 읽기 실패: {}", storageRef, e);

            throw new IllegalStateException(
                    "다운로드할 이미지 파일을 찾을 수 없습니다: " + storageRef,
                    e
            );
        }
    }

    @Override
    public String getContentType(String imageUrl) {
        Blob blob = storage.get(
                BlobId.of(bucket, resolveObjectName(imageUrl))
        );

        String contentType =
                blob == null
                        ? null
                        : blob.getContentType();

        if (contentType == null ||
                !ImageUploadValidator.ALLOWED_CONTENT_TYPES.contains(contentType)) {

            throw new CustomException(
                    ErrorCode.UNSUPPORTED_IMAGE_TYPE
            );
        }

        return contentType;
    }

    @Override
    public StoredImage save(byte[] imageBytes, String fileName) {
        String objectName = generatedPrefix + "/" + fileName;

        try {
            // 확장자가 아니라 실제 바이트로 판정한다. 객체 메타데이터의 Content-Type을
            // Meta가 그대로 읽어가므로 내용과 어긋나면 발행이 거부된다.
            upload(objectName, imageBytes, ImageBytes.contentType(imageBytes));

        } catch (StorageException e) {
            log.error("생성된 이미지 저장 실패: {}", objectName, e);

            throw new IllegalStateException("이미지 저장에 실패했습니다: " + fileName, e);
        }

        return new StoredImage(
                publicUrl(objectName),
                "gs://" + bucket + "/" + objectName
        );
    }

    private void upload(String objectName, byte[] bytes, String contentType) {
        storage.create(
                BlobInfo.newBuilder(BlobId.of(bucket, objectName))
                        .setContentType(contentType)
                        .build(),
                bytes
        );
    }

    private String publicUrl(String objectName) {
        return PUBLIC_BASE + bucket + "/" + objectName;
    }

    /**
     * 공개 URL이 업로드용인지 생성 이미지용인지 구분해 객체 이름으로 되돌린다.
     * 로컬 구현과 동일하게, 크롭 결과처럼 생성 프리픽스에 있는 이미지도 다시 읽을 수 있어야 한다.
     */
    String resolveObjectName(String imageUrl) {
        String fileName = ImageUploadValidator.extractFileName(imageUrl);

        String prefix =
                imageUrl.startsWith(publicUrl(generatedPrefix) + "/")
                        ? generatedPrefix
                        : uploadPrefix;

        return prefix + "/" + fileName;
    }

    /**
     * {@code gs://버킷/객체경로} 형태의 storageRef를 BlobId로 변환한다.
     */
    static BlobId parseBlobId(String storageRef) {
        if (storageRef == null || !storageRef.startsWith("gs://")) {
            throw new IllegalStateException("GCS 스토리지 참조가 아닙니다: " + storageRef);
        }

        String withoutScheme = storageRef.substring("gs://".length());
        int separator = withoutScheme.indexOf('/');

        if (separator <= 0 || separator == withoutScheme.length() - 1) {
            throw new IllegalStateException("GCS 스토리지 참조가 올바르지 않습니다: " + storageRef);
        }

        return BlobId.of(
                withoutScheme.substring(0, separator),
                withoutScheme.substring(separator + 1)
        );
    }
}
