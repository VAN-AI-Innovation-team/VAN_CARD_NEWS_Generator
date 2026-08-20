package com.van.cardnews.domain.generatedimage.service;

import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class GeneratedImageZipService {

    public byte[] zip(List<GeneratedCardImage> images) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (GeneratedCardImage image : images) {
                // [확인 필요] image.getStorageRef()가 로컬 절대경로라는 전제.
                // 오브젝트 스토리지로 교체되면 이 부분도 함께 바뀌어야 함.
                Path path = Path.of(image.getStorageRef());
                String entryName = image.getCardType().name().toLowerCase()
                        + "-" + image.getCardIndex() + ".png";
                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(Files.readAllBytes(path));
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("일괄 다운로드 ZIP 생성에 실패했습니다.", e);
        }
    }
}
