package com.van.cardnews.global.storage;

import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
@Profile("dev")
public class LocalImageStorageService implements ImageStorageService {

    private final Path uploadPath;
    private final String uploadPublicBaseUrl;

    private final Path generatedPath;
    private final String generatedPublicBaseUrl;

    public LocalImageStorageService(
            @Value("${app.upload.dir}") String uploadDir,
            @Value("${app.upload.public-base-url}") String uploadPublicBaseUrl,
            @Value("${app.generated.dir}") String generatedDir,
            @Value("${app.generated.public-base-url}") String generatedPublicBaseUrl
    ) {
        this.uploadPath = Paths.get(uploadDir);
        this.uploadPublicBaseUrl = uploadPublicBaseUrl;
        this.generatedPath = Paths.get(generatedDir);
        this.generatedPublicBaseUrl = generatedPublicBaseUrl;

        try {
            Files.createDirectories(this.uploadPath);
            Files.createDirectories(this.generatedPath);
        } catch (IOException e) {
            throw new IllegalStateException("이미지 저장 디렉토리를 생성할 수 없습니다.", e);
        }
    }

    @Override
    public String store(MultipartFile file) {
        ImageUploadValidator.validate(file);

        try {
            String extension =
                    ImageUploadValidator.extractExtension(
                            file.getOriginalFilename()
                    );

            String storedFileName =
                    UUID.randomUUID() + extension;

            Path targetPath =
                    uploadPath.resolve(storedFileName);

            Files.copy(
                    file.getInputStream(),
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return uploadPublicBaseUrl + "/" + storedFileName;

        } catch (IOException e) {
            log.error(
                    "이미지 저장 실패: {}",
                    e.getMessage(),
                    e
            );

            throw new CustomException(
                    ErrorCode.IMAGE_UPLOAD_FAILED
            );
        }
    }

    @Override
    public byte[] read(String imageUrl) {
        try {
            Path imagePath =
                    resolveStoredPath(imageUrl);

            return Files.readAllBytes(imagePath);

        } catch (IOException e) {
            log.error(
                    "이미지 읽기 실패: {}",
                    imageUrl,
                    e
            );

            throw new CustomException(
                    ErrorCode.IMAGE_UPLOAD_FAILED
            );
        }
    }

    @Override
    public byte[] readRef(String storageRef) {
        try {
            return Files.readAllBytes(Paths.get(storageRef));

        } catch (IOException e) {
            log.error(
                    "이미지 읽기 실패: {}",
                    storageRef,
                    e
            );

            throw new IllegalStateException(
                    "다운로드할 이미지 파일을 찾을 수 없습니다: " + storageRef,
                    e
            );
        }
    }

    @Override
    public String getContentType(String imageUrl) {
        try {
            Path imagePath =
                    resolveStoredPath(imageUrl);

            String contentType =
                    Files.probeContentType(imagePath);

            if (contentType == null ||
                    !ImageUploadValidator.ALLOWED_CONTENT_TYPES.contains(contentType)) {

                throw new CustomException(
                        ErrorCode.UNSUPPORTED_IMAGE_TYPE
                );
            }

            return contentType;

        } catch (IOException e) {
            log.error(
                    "이미지 형식 확인 실패: {}",
                    imageUrl,
                    e
            );

            throw new CustomException(
                    ErrorCode.UNSUPPORTED_IMAGE_TYPE
            );
        }
    }

    @Override
    public StoredImage save(byte[] imageBytes, String fileName) {
        try {
            Path targetPath = generatedPath.resolve(fileName);
            Files.write(targetPath, imageBytes);
            String publicUrl = generatedPublicBaseUrl + "/" + fileName;
            return new StoredImage(publicUrl, targetPath.toString());
        } catch (IOException e) {
            log.error("생성된 이미지 저장 실패: {}", fileName, e);
            throw new IllegalStateException("이미지 저장에 실패했습니다: " + fileName, e);
        }
    }

    /**
     * 공개 URL이 업로드용인지 생성 이미지용인지 base URL로 구분해 실제 저장 경로로 해석한다.
     * (크롭 결과처럼 생성 디렉터리에 저장된 이미지를 다시 읽을 수 있어야 한다.)
     */
    private Path resolveStoredPath(String imageUrl) {
        String fileName =
                ImageUploadValidator.extractFileName(imageUrl);

        Path basePath =
                imageUrl.startsWith(generatedPublicBaseUrl)
                        ? generatedPath
                        : uploadPath;

        return basePath
                .resolve(fileName)
                .normalize();
    }
}
