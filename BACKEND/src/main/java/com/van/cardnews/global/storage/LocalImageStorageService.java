package com.van.cardnews.global.storage;

import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class LocalImageStorageService implements ImageStorageService {

    private static final List<String> ALLOWED_CONTENT_TYPES =
            List.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp"
            );

    private static final long MAX_FILE_SIZE_BYTES =
            10L * 1024 * 1024;

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
        validate(file);

        try {
            String extension =
                    extractExtension(
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
    public String getContentType(String imageUrl) {
        try {
            Path imagePath =
                    resolveStoredPath(imageUrl);

            String contentType =
                    Files.probeContentType(imagePath);

            if (contentType == null ||
                    !ALLOWED_CONTENT_TYPES.contains(contentType)) {

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

    private Path resolveStoredPath(String imageUrl) {
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

        return uploadPath
                .resolve(fileName)
                .normalize();
    }

    private void validate(MultipartFile file) {
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

    private String extractExtension(
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
}
