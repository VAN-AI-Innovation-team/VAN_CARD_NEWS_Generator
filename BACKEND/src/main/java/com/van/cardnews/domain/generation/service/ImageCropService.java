package com.van.cardnews.domain.generation.service;

import com.van.cardnews.domain.content.entity.ContentImage;
import com.van.cardnews.domain.generation.dto.response.CardGenerationResult;
import com.van.cardnews.global.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@Service
@RequiredArgsConstructor
public class ImageCropService {

    private final ImageStorageService imageStorageService;

    /**
     * 원본 이미지에서 targetRatio를 유지하는
     * 최대 영역을 중앙 기준으로 계산합니다.
     *
     * 실제 이미지 파일 생성은 하지 않고
     * cropArea만 반환합니다.
     */
    public CardGenerationResult.CropArea calculateCropArea(
            ContentImage image,
            String cropRatio
    ) {
        if (cropRatio == null || cropRatio.isBlank()) {
            throw new IllegalArgumentException(
                    "템플릿의 cropRatio가 없습니다."
            );
        }

        double targetRatio =
                parseRatio(cropRatio);

        byte[] imageBytes =
                imageStorageService.read(
                        image.getImageUrl()
                );

        BufferedImage bufferedImage;

        try {
            bufferedImage =
                    ImageIO.read(
                            new ByteArrayInputStream(imageBytes)
                    );
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "이미지 크기를 읽을 수 없습니다.",
                    e
            );
        }

        if (bufferedImage == null) {
            throw new IllegalArgumentException(
                    "유효하지 않은 이미지입니다."
            );
        }

        int originalWidth =
                bufferedImage.getWidth();

        int originalHeight =
                bufferedImage.getHeight();

        double originalRatio =
                (double) originalWidth /
                        originalHeight;

        double cropWidth;
        double cropHeight;
        double x;
        double y;

        if (originalRatio > targetRatio) {
            /*
             * 원본이 더 가로로 긴 경우
             * 좌우를 잘라냅니다.
             */
            cropHeight = originalHeight;
            cropWidth =
                    cropHeight * targetRatio;

            x =
                    (originalWidth - cropWidth) / 2.0;
            y = 0;

        } else {
            /*
             * 원본이 더 세로로 긴 경우
             * 상하를 잘라냅니다.
             */
            cropWidth = originalWidth;
            cropHeight =
                    cropWidth / targetRatio;

            x = 0;
            y =
                    (originalHeight - cropHeight) / 2.0;
        }

        return new CardGenerationResult.CropArea(
                x,
                y,
                cropWidth,
                cropHeight
        );
    }

    private double parseRatio(String cropRatio) {
        String[] parts =
                cropRatio.split(":");

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "올바르지 않은 cropRatio입니다: "
                            + cropRatio
            );
        }

        double width =
                Double.parseDouble(parts[0]);

        double height =
                Double.parseDouble(parts[1]);

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "cropRatio 값은 0보다 커야 합니다."
            );
        }

        return width / height;
    }
}
