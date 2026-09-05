package com.van.cardnews.domain.generatedimage.service;

import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import com.van.cardnews.global.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class GeneratedImageZipService {

    private final ImageStorageService imageStorageService;

    public byte[] zip(List<GeneratedCardImage> images) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (GeneratedCardImage image : images) {
                String entryName = image.getCardType().name().toLowerCase()
                        + "-" + image.getCardIndex() + ".png";
                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(imageStorageService.readRef(image.getStorageRef()));
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("일괄 다운로드 ZIP 생성에 실패했습니다.", e);
        }
    }
}
