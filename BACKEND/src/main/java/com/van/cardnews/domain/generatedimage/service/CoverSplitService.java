package com.van.cardnews.domain.generatedimage.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class CoverSplitService {

    /**
     * 가로 3배로 생성된 cover 이미지를 정확히 3등분한다.
     * (content/closing 카드는 이 서비스의 대상이 아니다.)
     */
    public List<byte[]> splitIntoThree(byte[] wideCoverBytes, int singleWidth, int height) {
        try {
            BufferedImage wide = ImageIO.read(new ByteArrayInputStream(wideCoverBytes));
            if (wide == null) {
                throw new IllegalArgumentException("cover 이미지를 읽을 수 없습니다.");
            }
            int expectedWidth = singleWidth * 3;
            if (wide.getWidth() != expectedWidth || wide.getHeight() != height) {
                throw new IllegalArgumentException(
                        "3분할 대상 이미지 크기가 예상과 다릅니다. expected="
                                + expectedWidth + "x" + height
                                + ", actual=" + wide.getWidth() + "x" + wide.getHeight()
                );
            }

            List<byte[]> pieces = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                BufferedImage piece = wide.getSubimage(i * singleWidth, 0, singleWidth, height);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(piece, "png", baos);
                pieces.add(baos.toByteArray());
            }
            return pieces;
        } catch (IOException e) {
            throw new IllegalStateException("cover 3분할 중 오류가 발생했습니다.", e);
        }
    }
}
