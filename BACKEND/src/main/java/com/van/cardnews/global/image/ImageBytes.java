package com.van.cardnews.global.image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * 이미지를 거부한다. 같은 이유로 변환 결과의 크기도 호출자가 알던 값이 아니라
 * 실제 이미지에서 읽어 돌려준다.
 */
public final class ImageBytes {

    /** 텍스트가 많은 카드에서 링잉이 눈에 띄지 않으면서 8MB 제한에 여유가 큰 지점. */
    private static final float JPEG_QUALITY = 0.92f;

    /**
     * Meta 이미지 규격의 폭 상한. dev 프로필의 1080px 카드에서는 아무 일도 하지 않지만,
     * prod의 {@code HiggsfieldClientImpl}은 {@code resolution: "2K"}로 요청하므로
     * 실제로 이 값을 넘는 이미지가 들어온다.
     */
    private static final int MAX_WIDTH = 1440;

    private ImageBytes() {
    }

    /**
     * 발행 가능한 JPEG와 그 실제 크기.
     *
     * 크기를 함께 돌려주는 이유는, 호출자가 알고 있던 크기(템플릿 캔버스 값 등)가
     * 변환 결과와 다를 수 있기 때문이다. DB에는 파일의 실제 크기가 들어가야 한다.
     */
    public record Jpeg(byte[] bytes, int width, int height) {}

    /**
     * 알파를 흰 배경으로 합성하고, 폭이 {@value #MAX_WIDTH}px를 넘으면 비율을 유지한 채
     * 축소한 뒤 sRGB JPEG로 인코딩한다. 상한 이하의 이미지는 확대하지 않는다.
     */
    public static Jpeg toJpeg(byte[] source) {
        BufferedImage decoded = decode(source);

        int targetWidth = Math.min(decoded.getWidth(), MAX_WIDTH);
        int targetHeight =
                targetWidth == decoded.getWidth()
                        ? decoded.getHeight()
                        : Math.max(1, Math.round(
                                decoded.getHeight() * (float) targetWidth / decoded.getWidth()
                        ));

        // TYPE_INT_RGB는 알파가 없는 sRGB 색공간이다.
        BufferedImage flattened =
                new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = flattened.createGraphics();
        try {
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.drawImage(decoded, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }

        return new Jpeg(encodeJpeg(flattened), targetWidth, targetHeight);
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
