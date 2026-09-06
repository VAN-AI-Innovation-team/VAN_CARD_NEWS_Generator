package com.van.cardnews.global.image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLConnection;

/**
 * 이미지 바이트 배열에 대한 포맷 판정과 발행용 JPEG 변환.
 *
 * Instagram은 알파 채널이 없는 sRGB JPEG만 받는다. 카드 렌더링은
 * {@code TYPE_INT_ARGB}로 이루어지므로 알파를 그대로 JPEG로 넘기면 검게 나온다.
 *
 * 포맷은 파일 확장자가 아니라 실제 바이트로 판정한다. 확장자는 저장 시점에 붙이는
 * 문자열일 뿐이라 내용과 어긋날 수 있고, 그 상태로 Content-Type을 내면 Meta가
 * 이미지를 거부한다.
 */
public final class ImageBytes {

    /** 텍스트가 많은 카드에서 링잉이 눈에 띄지 않으면서 8MB 제한에 여유가 큰 지점. */
    private static final float JPEG_QUALITY = 0.92f;

    private ImageBytes() {
    }

    /**
     * 알파를 흰 배경으로 합성한 뒤 sRGB JPEG로 인코딩한다. 원본 크기(비율)는 유지된다.
     */
    public static byte[] toJpeg(byte[] source) {
        BufferedImage decoded = decode(source);

        // TYPE_INT_RGB는 알파가 없는 sRGB 색공간이다.
        BufferedImage flattened = new BufferedImage(
                decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = flattened.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, flattened.getWidth(), flattened.getHeight());
            g.drawImage(decoded, 0, 0, null);
        } finally {
            g.dispose();
        }

        return encodeJpeg(flattened);
    }

    /**
     * 실제 바이트로 판정한 MIME 타입.
     */
    public static String contentType(byte[] bytes) {
        String detected;

        try {
            detected = URLConnection.guessContentTypeFromStream(
                    new ByteArrayInputStream(bytes)
            );
        } catch (IOException e) {
            throw new IllegalStateException("이미지 포맷을 판정할 수 없습니다.", e);
        }

        if (detected == null) {
            throw new IllegalStateException("이미지 포맷을 판정할 수 없습니다.");
        }

        return detected;
    }

    /**
     * 실제 바이트에 맞는 파일 확장자(선행 점 포함).
     */
    public static String extension(byte[] bytes) {
        String contentType = contentType(bytes);

        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> throw new IllegalStateException("지원하지 않는 이미지 포맷입니다: " + contentType);
        };
    }

    private static BufferedImage decode(byte[] source) {
        BufferedImage decoded;

        try {
            decoded = ImageIO.read(new ByteArrayInputStream(source));
        } catch (IOException e) {
            throw new IllegalStateException("이미지를 읽을 수 없습니다.", e);
        }

        if (decoded == null) {
            throw new IllegalStateException("이미지로 해석할 수 없는 바이트입니다.");
        }

        return decoded;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();

        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("JPEG 인코딩에 실패했습니다.", e);
        } finally {
            writer.dispose();
        }

        return output.toByteArray();
    }
}
