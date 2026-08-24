package com.van.cardnews.domain.generatedimage.service;

import com.van.cardnews.global.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * 원본 이미지에서 사용자가 지정한 백분율 크롭 영역만 잘라
 * 카드 이미지 생성기에 전달할 수 있는 PNG로 저장합니다.
 */
@Service
@RequiredArgsConstructor
public class ImageCropService {

    private final ImageStorageService imageStorageService;

    public String cropToPublicUrl(
            String imageUrl,
            double x,
            double y,
            double width,
            double height
    ) {
        if (isFullImage(x, y, width, height)) {
            return imageUrl;
        }

        try {
            byte[] sourceBytes = imageStorageService.read(imageUrl);
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(sourceBytes));

            if (source == null) {
                throw new IllegalArgumentException("이미지를 읽을 수 없습니다: " + imageUrl);
            }

            int cropX = clamp((int) Math.floor(source.getWidth() * x / 100.0), 0, source.getWidth() - 1);
            int cropY = clamp((int) Math.floor(source.getHeight() * y / 100.0), 0, source.getHeight() - 1);

            int cropWidth = Math.max(
                    1,
                    Math.min(
                            source.getWidth() - cropX,
                            (int) Math.round(source.getWidth() * width / 100.0)
                    )
            );

            int cropHeight = Math.max(
                    1,
                    Math.min(
                            source.getHeight() - cropY,
                            (int) Math.round(source.getHeight() * height / 100.0)
                    )
            );

            BufferedImage cropped = source.getSubimage(
                    cropX,
                    cropY,
                    cropWidth,
                    cropHeight
            );

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(cropped, "png", output);

            ImageStorageService.StoredImage stored = imageStorageService.save(
                    output.toByteArray(),
                    "content-crop-" + UUID.randomUUID() + ".png"
            );

            return stored.publicUrl();
        } catch (IOException e) {
            throw new IllegalStateException("이미지 크롭 처리에 실패했습니다.", e);
        }
    }

    private boolean isFullImage(
            double x,
            double y,
            double width,
            double height
    ) {
        return x == 0.0 && y == 0.0 && width == 100.0 && height == 100.0;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
