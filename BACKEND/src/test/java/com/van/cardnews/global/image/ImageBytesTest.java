package com.van.cardnews.global.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 발행용 JPEG 변환 검증.
 *
 * MockHiggsfieldClient가 TYPE_INT_ARGB로 렌더링하므로 카드 바이트에는 알파가 실제로 있고,
 * 합성 없이 JPEG로 넘기면 해당 영역이 검게 나온다. 그래서 "JPEG가 되었는가"가 아니라
 * "투명 영역이 흰색이 되었는가"까지 단언한다.
 */
class ImageBytesTest {

    @Test
    void 투명_ARGB_PNG를_흰_배경_JPEG로_변환한다() throws IOException {
        byte[] png = transparentArgbPng(1080, 1350);

        byte[] jpeg = ImageBytes.toJpeg(png);

        assertThat(ImageBytes.contentType(jpeg)).isEqualTo("image/jpeg");

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));

        // 비율(1:1 / 4:5) 유지
        assertThat(decoded.getWidth()).isEqualTo(1080);
        assertThat(decoded.getHeight()).isEqualTo(1350);

        // 알파가 남아 있으면 JPEG 디코딩 결과가 4채널이 되거나 검게 나온다.
        assertThat(decoded.getColorModel().hasAlpha()).isFalse();
        assertThat(decoded.getColorModel().getColorSpace().isCS_sRGB()).isTrue();

        // 원본이 완전 투명이던 좌상단은 흰색이어야 한다. (검게 나오는 회귀를 잡는 단언)
        Color topLeft = new Color(decoded.getRGB(0, 0));
        assertThat(topLeft.getRed()).isGreaterThan(245);
        assertThat(topLeft.getGreen()).isGreaterThan(245);
        assertThat(topLeft.getBlue()).isGreaterThan(245);

        // 불투명하게 칠한 영역의 색은 유지된다.
        Color painted = new Color(decoded.getRGB(600, 700));
        assertThat(painted.getRed()).isLessThan(60);
        assertThat(painted.getGreen()).isLessThan(60);
        assertThat(painted.getBlue()).isLessThan(60);
    }

    @Test
    void 확장자는_실제_바이트로_판정한다() throws IOException {
        byte[] png = transparentArgbPng(10, 10);

        assertThat(ImageBytes.contentType(png)).isEqualTo("image/png");
        assertThat(ImageBytes.extension(png)).isEqualTo(".png");
        assertThat(ImageBytes.extension(ImageBytes.toJpeg(png))).isEqualTo(".jpg");
    }

    @Test
    void 이미지가_아닌_바이트는_거부한다() {
        byte[] garbage = "not an image".getBytes();

        assertThatThrownBy(() -> ImageBytes.contentType(garbage))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> ImageBytes.toJpeg(garbage))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 좌상단은 완전 투명, 중앙 절반은 불투명 검정인 ARGB PNG. */
    private byte[] transparentArgbPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillRect(width / 2, height / 2, width / 2, height / 2);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
