package com.van.cardnews.global.ai.higgsfield;

import com.van.cardnews.global.ai.higgsfield.dto.HiggsfieldGenerationRequest;
import com.van.cardnews.global.ai.higgsfield.dto.HiggsfieldGenerationResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("dev")
public class MockHiggsfieldClient implements HiggsfieldClient {

    @Override
    public HiggsfieldGenerationResult generateCardImages(HiggsfieldGenerationRequest request) {
        List<HiggsfieldGenerationResult.GeneratedCard> cards = new ArrayList<>();

        int width = request.canvasWidth();
        int height = request.canvasHeight();

        // 1) cover
        cards.add(new HiggsfieldGenerationResult.GeneratedCard(
                HiggsfieldGenerationResult.GeneratedCard.CardType.COVER,
                0,
                renderPlaceholder("COVER", width, height, new Color(0x3A, 0x6E, 0xA5)),
                width,
                height
        ));

        // 2) content — Mock 테스트를 위해 최소 2장 고정 생성
        //    [확인 필요] 실제로는 request.cardGenerationResult()에서 본문 카드 개수를 파싱해야 함
        for (int i = 0; i < 2; i++) {
            cards.add(new HiggsfieldGenerationResult.GeneratedCard(
                    HiggsfieldGenerationResult.GeneratedCard.CardType.CONTENT,
                    i,
                    renderPlaceholder("CONTENT " + i, width, height, new Color(0x4C, 0xAF, 0x50)),
                    width,
                    height
            ));
        }

        // 3) closing
        cards.add(new HiggsfieldGenerationResult.GeneratedCard(
                HiggsfieldGenerationResult.GeneratedCard.CardType.CLOSING,
                0,
                renderPlaceholder("CLOSING", width, height, new Color(0x21, 0x21, 0x21)),
                width,
                height
        ));

        return new HiggsfieldGenerationResult(cards);
    }

    private byte[] renderPlaceholder(String label, int width, int height, Color background) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(background);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(24, width / 20)));
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(label);
        g.drawString(label, (width - textWidth) / 2, height / 2);
        g.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Mock 이미지 생성 실패", e);
        }
    }
}
