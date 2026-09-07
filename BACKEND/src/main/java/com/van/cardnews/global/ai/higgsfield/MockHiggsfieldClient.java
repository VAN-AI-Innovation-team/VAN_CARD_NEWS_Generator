package com.van.cardnews.global.ai.higgsfield;

import com.fasterxml.jackson.databind.JsonNode;
import com.van.cardnews.global.ai.higgsfield.dto.HiggsfieldGenerationRequest;
import com.van.cardnews.global.ai.higgsfield.dto.HiggsfieldGenerationResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Profile("dev")
public class MockHiggsfieldClient implements HiggsfieldClient {

    private static final Color FALLBACK_BACKGROUND =
            new Color(255, 255, 255);

    private static final Color FALLBACK_TEXT =
            new Color(23, 32, 51);

    @Override
    public HiggsfieldGenerationResult generateCardImages(
            HiggsfieldGenerationRequest request
    ) {
        List<HiggsfieldGenerationResult.GeneratedCard> cards =
                new ArrayList<>();

        JsonNode cardGenerationResult =
                request.cardGenerationResult();

        JsonNode layoutDefinition =
                request.layoutDefinition();

        JsonNode designTokens =
                request.designTokens();

        JsonNode resolvedImagePlacements =
                request.resolvedImagePlacements();

        int width = request.canvasWidth();
        int height = request.canvasHeight();

        /*
         * ---------------------------------------------------------
         * COVER
         * ---------------------------------------------------------
         */
        cards.add(
                renderCard(
                        HiggsfieldGenerationResult.GeneratedCard.CardType.COVER,
                        0,
                        cardGenerationResult.path("cover"),
                        layoutDefinition
                                .path("cards")
                                .path("cover"),
                        designTokens,
                        resolvedImagePlacements,
                        width,
                        height
                )
        );

        /*
         * ---------------------------------------------------------
         * CONTENT
         * ---------------------------------------------------------
         */
        JsonNode contentCards =
                cardGenerationResult.path("content");

        if (contentCards.isArray()) {
            for (int i = 0; i < contentCards.size(); i++) {
                cards.add(
                        renderCard(
                                HiggsfieldGenerationResult.GeneratedCard.CardType.CONTENT,
                                i,
                                contentCards.get(i),
                                layoutDefinition
                                        .path("cards")
                                        .path("content"),
                                designTokens,
                                resolvedImagePlacements,
                                width,
                                height
                        )
                );
            }
        }

        /*
         * ---------------------------------------------------------
         * CLOSING
         * ---------------------------------------------------------
         */
        cards.add(
                renderCard(
                        HiggsfieldGenerationResult.GeneratedCard.CardType.CLOSING,
                        0,
                        cardGenerationResult.path("closing"),
                        layoutDefinition
                                .path("cards")
                                .path("closing"),
                        designTokens,
                        resolvedImagePlacements,
                        width,
                        height
                )
        );

        return new HiggsfieldGenerationResult(cards);
    }

    /**
     * 실제 템플릿의 layoutDefinition + designTokens를 이용하여
     * 카드 이미지를 렌더링합니다.
     *
     * Mock이므로 Higgsfield AI 대신 Java2D로 카드 결과물을 만듭니다.
     *
     * 따라서:
     * - 템플릿 배경
     * - panel / overlay / divider
     * - 제목 / 본문 / 강조문구 / CTA
     * - 실제 업로드 이미지
     * - 카드별 위치
     * - 템플릿 색상
     *
     * 을 모두 반영합니다.
     */
    private HiggsfieldGenerationResult.GeneratedCard renderCard(
            HiggsfieldGenerationResult.GeneratedCard.CardType cardType,
            int cardIndex,
            JsonNode cardData,
            JsonNode layoutCard,
            JsonNode designTokens,
            JsonNode resolvedImagePlacements,
            int width,
            int height
    ) {
        BufferedImage image =
                new BufferedImage(
                        width,
                        height,
                        BufferedImage.TYPE_INT_ARGB
                );

        Graphics2D g =
                image.createGraphics();

        try {
            configureGraphics(g);

            drawBackground(
                    g,
                    layoutCard,
                    designTokens,
                    width,
                    height
            );

            JsonNode elements =
                    layoutCard.path("elements");

            if (elements.isObject()) {
                List<JsonNode> orderedElements =
                        new ArrayList<>();

                elements
                        .elements()
                        .forEachRemaining(orderedElements::add);

                orderedElements.sort(
                        (a, b) ->
                                Integer.compare(
                                        a.path("layer").asInt(0),
                                        b.path("layer").asInt(0)
                                )
                );

                for (JsonNode element : orderedElements) {
                    drawElement(
                            g,
                            element,
                            cardData,
                            designTokens,
                            resolvedImagePlacements,
                            cardType,
                            cardIndex,
                            width,
                            height
                    );
                }
            }

        } finally {
            g.dispose();
        }

        try {
            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            ImageIO.write(
                    image,
                    "png",
                    output
            );

            return new HiggsfieldGenerationResult.GeneratedCard(
                    cardType,
                    cardIndex,
                    output.toByteArray()
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Mock 이미지 생성 실패",
                    e
            );
        }
    }

    private void configureGraphics(Graphics2D g) {
        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        g.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
        );

        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
        );
    }

    /**
     * layoutDefinition의 background를 기본 배경으로 사용합니다.
     */
    private void drawBackground(
            Graphics2D g,
            JsonNode layoutCard,
            JsonNode designTokens,
            int width,
            int height
    ) {
        JsonNode background =
                layoutCard.path("background");

        Color backgroundColor =
                resolveBackgroundColor(
                        background,
                        designTokens
                );

        g.setColor(backgroundColor);

        g.fillRect(
                0,
                0,
                width,
                height
        );
    }

    private Color resolveBackgroundColor(
            JsonNode background,
            JsonNode designTokens
    ) {
        if (background.isTextual()) {
            return parseColor(
                    background.asText(),
                    FALLBACK_BACKGROUND
            );
        }

        if (background.isObject()) {
            String value =
                    background
                            .path("value")
                            .asText(null);

            if (value != null && !value.isBlank()) {
                return parseColor(
                        value,
                        FALLBACK_BACKGROUND
                );
            }

            String token =
                    background
                            .path("colorToken")
                            .asText(null);

            if (token != null) {
                return resolveTokenColor(
                        designTokens,
                        token,
                        FALLBACK_BACKGROUND
                );
            }
        }

        return FALLBACK_BACKGROUND;
    }

    private void drawElement(
            Graphics2D g,
            JsonNode element,
            JsonNode cardData,
            JsonNode designTokens,
            JsonNode resolvedImagePlacements,
            HiggsfieldGenerationResult.GeneratedCard.CardType cardType,
            int cardIndex,
            int canvasWidth,
            int canvasHeight
    ) {
        double x =
                element.path("x").asDouble(0)
                        / 100.0
                        * canvasWidth;

        double y =
                element.path("y").asDouble(0)
                        / 100.0
                        * canvasHeight;

        double width =
                element.path("width").asDouble(100)
                        / 100.0
                        * canvasWidth;

        double height =
                element.path("height").asDouble(100)
                        / 100.0
                        * canvasHeight;

        int ix =
                (int) Math.round(x);

        int iy =
                (int) Math.round(y);

        int iw =
                Math.max(
                        1,
                        (int) Math.round(width)
                );

        int ih =
                Math.max(
                        1,
                        (int) Math.round(height)
                );

        String role =
                element
                        .path("role")
                        .asText("")
                        .toLowerCase(Locale.ROOT);

        /*
         * IMAGE
         */
        if ("image".equals(role)) {
            drawImage(
                    g,
                    ix,
                    iy,
                    iw,
                    ih,
                    resolvedImagePlacements,
                    cardType,
                    cardIndex
            );

            return;
        }

        /*
         * SHAPE
         */
        if (isShapeRole(role)) {
            drawShape(
                    g,
                    element,
                    designTokens,
                    ix,
                    iy,
                    iw,
                    ih
            );

            return;
        }

        /*
         * TEXT
         */
        if (isTextRole(role)) {
            drawText(
                    g,
                    element,
                    cardData,
                    designTokens,
                    ix,
                    iy,
                    iw,
                    ih
            );
        }
    }

    private boolean isShapeRole(String role) {
        return "background".equals(role)
                || "overlay".equals(role)
                || "panel".equals(role)
                || "divider".equals(role)
                || "shape".equals(role);
    }

    private boolean isTextRole(String role) {
        return "title".equals(role)
                || "body".equals(role)
                || "highlight".equals(role)
                || "eyebrow".equals(role)
                || "footer".equals(role)
                || "badge".equals(role)
                || "decoration".equals(role)
                || "cta".equals(role)
                || "date".equals(role)
                || "location".equals(role)
                || "text".equals(role);
    }

    /**
     * panel / overlay / divider / background 등을 렌더링합니다.
     */
    private void drawShape(
            Graphics2D g,
            JsonNode element,
            JsonNode designTokens,
            int x,
            int y,
            int width,
            int height
    ) {
        String colorToken =
                element
                        .path("colorToken")
                        .asText(null);

        String backgroundColor =
                element
                        .path("backgroundColor")
                        .asText(null);

        Color color =
                resolveTokenColor(
                        designTokens,
                        colorToken,
                        parseColor(
                                backgroundColor,
                                new Color(
                                        0,
                                        0,
                                        0,
                                        0
                                )
                        )
                );

        g.setColor(color);

        String shape =
                element
                        .path("shape")
                        .asText(
                                element
                                        .path("shapeType")
                                        .asText("rectangle")
                        )
                        .toLowerCase(Locale.ROOT);

        if (
                "pill".equals(shape)
                        || "badge".equals(shape)
                        || "chip".equals(shape)
        ) {
            g.fill(
                    new RoundRectangle2D.Double(
                            x,
                            y,
                            width,
                            height,
                            height,
                            height
                    )
            );
        } else {
            g.fillRect(
                    x,
                    y,
                    width,
                    height
            );
        }
    }

    /**
     * 템플릿의 text 요소를 실제 카드 데이터와 함께 렌더링합니다.
     */
    private void drawText(
            Graphics2D g,
            JsonNode element,
            JsonNode cardData,
            JsonNode designTokens,
            int x,
            int y,
            int width,
            int height
    ) {
        String text =
                getElementText(
                        element,
                        cardData
                );

        if (text.isBlank()) {
            return;
        }

        /*
         * 텍스트 색상
         */
        Color textColor =
                resolveTokenColor(
                        designTokens,
                        element
                                .path("colorToken")
                                .asText(null),
                        FALLBACK_TEXT
                );

        /*
         * 텍스트 배경
         *
         * 예:
         * highlight의 backgroundToken
         */
        String backgroundToken =
                element
                        .path("backgroundToken")
                        .asText(null);

        if (
                backgroundToken != null
                        && !backgroundToken.isBlank()
        ) {
            Color background =
                    resolveTokenColor(
                            designTokens,
                            backgroundToken,
                            null
                    );

            if (background != null) {
                g.setColor(background);

                String shape =
                        element
                                .path("shape")
                                .asText("rectangle")
                                .toLowerCase(Locale.ROOT);

                if ("pill".equals(shape)
                        || "badge".equals(shape)
                        || "chip".equals(shape)) {
                    g.fill(
                            new RoundRectangle2D.Double(
                                    x,
                                    y,
                                    width,
                                    height,
                                    height,
                                    height
                            )
                    );
                } else {
                    g.fillRect(
                            x,
                            y,
                            width,
                            height
                    );
                }
            }
        }

        String typographyToken =
                element
                        .path("typographyToken")
                        .asText(null);

        JsonNode typography =
                designTokens.path("typography");

        int fontSize =
                resolveFontSize(
                        typography,
                        typographyToken,
                        element
                                .path("fontSize")
                                .asInt(32)
                );

        int fontStyle =
                resolveFontStyle(
                        typography,
                        typographyToken
                );

        Font font =
                loadFont(
                        fontStyle,
                        fontSize
                );

        g.setFont(font);
        g.setColor(textColor);

        String align =
                element
                        .path("align")
                        .asText("left");

        List<String> lines =
                wrapText(
                        g.getFontMetrics(),
                        text,
                        width
                );

        int lineHeight =
                resolveLineHeight(
                        typography,
                        typographyToken,
                        g.getFontMetrics()
                                .getHeight()
                );

        int maxLines =
                Math.max(
                        1,
                        height / lineHeight
                );

        if (lines.size() > maxLines) {
            lines =
                    new ArrayList<>(
                            lines.subList(
                                    0,
                                    maxLines
                            )
                    );
        }

        int totalHeight =
                lines.size() * lineHeight;

        /*
         * CSS preview와 비슷하게
         * title / highlight / badge는 세로 중앙,
         * body / eyebrow 등은 위쪽에 배치합니다.
         */
        boolean centerVertically =
                "title".equals(element.path("role").asText())
                        || "highlight".equals(element.path("role").asText())
                        || "badge".equals(element.path("role").asText());

        int baseline;

        if (centerVertically) {
            baseline =
                    y
                            + Math.max(
                            g.getFontMetrics().getAscent(),
                            (height - totalHeight) / 2
                                    + g.getFontMetrics().getAscent()
                    );
        } else {
            baseline =
                    y
                            + g.getFontMetrics().getAscent();
        }

        for (String line : lines) {
            int textWidth =
                    g.getFontMetrics()
                            .stringWidth(line);

            int drawX;

            if ("center".equalsIgnoreCase(align)) {
                drawX =
                        x
                                + Math.max(
                                0,
                                (width - textWidth) / 2
                        );
            } else if ("right".equalsIgnoreCase(align)) {
                drawX =
                        x
                                + Math.max(
                                0,
                                width - textWidth
                        );
            } else {
                drawX = x;
            }

            g.drawString(
                    line,
                    drawX,
                    baseline
            );

            baseline += lineHeight;
        }
    }

    private String getElementText(
            JsonNode element,
            JsonNode card
    ) {
        String contentField =
                element
                        .path("contentField")
                        .asText(null);

        String role =
                element
                        .path("role")
                        .asText("");

        /*
         * contentField가 있으면
         * 해당 필드를 최우선으로 사용합니다.
         */
        if (
                contentField != null
                        && !contentField.isBlank()
        ) {
            return card
                    .path(contentField)
                    .asText("");
        }

        /*
         * contentField가 없어도
         * 기본 role 이름과 카드 데이터가 동일하면
         * 그대로 사용합니다.
         */
        if (
                "title".equals(role)
                        || "body".equals(role)
                        || "highlight".equals(role)
                        || "date".equals(role)
                        || "location".equals(role)
                        || "cta".equals(role)
        ) {
            return card
                    .path(role)
                    .asText("");
        }

        /*
         * 템플릿에 고정된 문구
         * 예:
         * MEMBER RECRUITMENT
         * VAN
         */
        return element
                .path("text")
                .asText("");
    }

    /**
     * 카드에 연결된 실제 업로드 이미지를 렌더링합니다.
     *
     * ImagePlacementResolver가 이미 crop된 URL을 전달하므로
     * 여기서는 해당 결과를 템플릿 image 영역에 맞춰 채웁니다.
     */
    private void drawImage(
            Graphics2D g,
            int x,
            int y,
            int width,
            int height,
            JsonNode resolvedImagePlacements,
            HiggsfieldGenerationResult.GeneratedCard.CardType cardType,
            int cardIndex
    ) {
        String imageUrl =
                findResolvedImageUrl(
                        resolvedImagePlacements,
                        cardType,
                        cardIndex
                );

        if (
                imageUrl == null
                        || imageUrl.isBlank()
        ) {
            return;
        }

        try {
            BufferedImage source =
                    readImage(imageUrl);

            if (source == null) {
                return;
            }

            BufferedImage cropped =
                    cropToFill(
                            source,
                            width,
                            height
                    );

            g.drawImage(
                    cropped,
                    x,
                    y,
                    width,
                    height,
                    null
            );

        } catch (Exception ignored) {
            /*
             * 이미지 하나가 문제가 있어도
             * 카드 전체 생성은 계속 진행합니다.
             */
        }
    }

    private String findResolvedImageUrl(
            JsonNode placements,
            HiggsfieldGenerationResult.GeneratedCard.CardType cardType,
            int cardIndex
    ) {
        if (
                placements == null
                        || !placements.isArray()
        ) {
            return null;
        }

        String expectedType =
                cardType
                        .name()
                        .toLowerCase(Locale.ROOT);

        for (JsonNode placement : placements) {
            String placementType =
                    placement
                            .path("cardType")
                            .asText("");

            int placementIndex =
                    placement
                            .path("cardIndex")
                            .asInt(-1);

            if (
                    expectedType.equals(placementType)
                            && cardIndex == placementIndex
            ) {
                return placement
                        .path("resolvedImageUrl")
                        .asText(null);
            }
        }

        return null;
    }

    private BufferedImage readImage(
            String imageUrl
    ) throws IOException {
        URLConnection connection =
                URI.create(imageUrl)
                        .toURL()
                        .openConnection();

        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        try (InputStream inputStream =
                     connection.getInputStream()) {

            return ImageIO.read(
                    inputStream
            );
        }
    }

    private BufferedImage cropToFill(
            BufferedImage source,
            int targetWidth,
            int targetHeight
    ) {
        double sourceRatio =
                (double) source.getWidth()
                        / source.getHeight();

        double targetRatio =
                (double) targetWidth
                        / targetHeight;

        int cropWidth =
                source.getWidth();

        int cropHeight =
                source.getHeight();

        int cropX = 0;
        int cropY = 0;

        if (sourceRatio > targetRatio) {
            cropWidth =
                    Math.max(
                            1,
                            (int) Math.round(
                                    source.getHeight()
                                            * targetRatio
                            )
                    );

            cropX =
                    (source.getWidth() - cropWidth) / 2;

        } else if (sourceRatio < targetRatio) {
            cropHeight =
                    Math.max(
                            1,
                            (int) Math.round(
                                    source.getWidth()
                                            / targetRatio
                            )
                    );

            cropY =
                    (source.getHeight() - cropHeight) / 2;
        }

        BufferedImage cropped =
                source.getSubimage(
                        cropX,
                        cropY,
                        cropWidth,
                        cropHeight
                );

        BufferedImage result =
                new BufferedImage(
                        targetWidth,
                        targetHeight,
                        BufferedImage.TYPE_INT_ARGB
                );

        Graphics2D g =
                result.createGraphics();

        try {
            configureGraphics(g);

            g.drawImage(
                    cropped,
                    0,
                    0,
                    targetWidth,
                    targetHeight,
                    null
            );

        } finally {
            g.dispose();
        }

        return result;
    }

    private int resolveFontSize(
            JsonNode typography,
            String typographyToken,
            int fallback
    ) {
        if (
                typographyToken == null
                        || typographyToken.isBlank()
        ) {
            return Math.max(
                    12,
                    fallback
            );
        }

        String raw =
                typography
                        .path(
                                typographyToken
                                        + "Size"
                        )
                        .asText("");

        if (raw.isBlank()) {
            return Math.max(
                    12,
                    fallback
            );
        }

        try {
            return Math.max(
                    10,
                    (int) Math.round(
                            Double.parseDouble(
                                    raw
                                            .replace(
                                                    "px",
                                                    ""
                                            )
                                            .trim()
                            )
                    )
            );

        } catch (NumberFormatException ignored) {
            return Math.max(
                    12,
                    fallback
            );
        }
    }

    private int resolveFontStyle(
            JsonNode typography,
            String typographyToken
    ) {
        if (
                typographyToken == null
                        || typographyToken.isBlank()
        ) {
            return Font.PLAIN;
        }

        String weight =
                typography
                        .path(
                                typographyToken
                                        + "Weight"
                        )
                        .asText("");

        try {
            int numericWeight =
                    Integer.parseInt(weight);

            return numericWeight >= 600
                    ? Font.BOLD
                    : Font.PLAIN;

        } catch (NumberFormatException ignored) {
            return weight
                    .toLowerCase(Locale.ROOT)
                    .contains("bold")
                    ? Font.BOLD
                    : Font.PLAIN;
        }
    }

    private int resolveLineHeight(
            JsonNode typography,
            String typographyToken,
            int fallback
    ) {
        if (
                typographyToken == null
                        || typographyToken.isBlank()
        ) {
            return fallback;
        }

        String raw =
                typography
                        .path(
                                typographyToken
                                        + "LineHeight"
                        )
                        .asText("");

        if (raw.isBlank()) {
            return fallback;
        }

        try {
            double lineHeight =
                    Double.parseDouble(
                            raw
                                    .replace(
                                            "px",
                                            ""
                                    )
                                    .trim()
                    );

            /*
             * 1.28 같은 배수라면
             * 현재 폰트 크기 기준으로 계산합니다.
             */
            if (lineHeight > 0 && lineHeight < 5) {
                String size =
                        typography
                                .path(
                                        typographyToken
                                                + "Size"
                                )
                                .asText("");

                double fontSize =
                        Double.parseDouble(
                                size
                                        .replace(
                                                "px",
                                                ""
                                        )
                                        .trim()
                        );

                return Math.max(
                        1,
                        (int) Math.round(
                                fontSize * lineHeight
                        )
                );
            }

            return Math.max(
                    1,
                    (int) Math.round(lineHeight)
            );

        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /**
     * Docker Alpine에서도 한글을 렌더링할 수 있도록
     * Noto Sans CJK KR을 최우선으로 사용합니다.
     */
    private Font loadFont(
            int style,
            int size
    ) {
        String[] candidates = {
                "Noto Sans CJK KR",
                "Noto Sans KR",
                "Malgun Gothic",
                "AppleGothic",
                "SansSerif"
        };

        for (String family : candidates) {
            Font font =
                    new Font(
                            family,
                            style,
                            size
                    );

            if (
                    "SansSerif".equals(family)
                            || !font
                            .getFamily()
                            .equalsIgnoreCase("Dialog")
            ) {
                return font;
            }
        }

        return new Font(
                "SansSerif",
                style,
                size
        );
    }

    /**
     * 한글을 포함한 텍스트를 카드 영역 안에서 줄바꿈합니다.
     */
    private List<String> wrapText(
            FontMetrics metrics,
            String text,
            int maxWidth
    ) {
        List<String> lines =
                new ArrayList<>();

        String normalized =
                text.replace(
                        "\r\n",
                        "\n"
                );

        for (
                String paragraph :
                normalized.split(
                        "\n",
                        -1
                )
        ) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }

            StringBuilder current =
                    new StringBuilder();

            for (
                    int i = 0;
                    i < paragraph.length();
                    i++
            ) {
                char character =
                        paragraph.charAt(i);

                String next =
                        current
                                + String.valueOf(
                                character
                        );

                if (
                        current.length() > 0
                                && metrics.stringWidth(
                                next
                        ) > maxWidth
                ) {
                    lines.add(
                            current.toString()
                    );

                    current.setLength(0);
                }

                current.append(character);
            }

            if (current.length() > 0) {
                lines.add(
                        current.toString()
                );
            }
        }

        return lines.isEmpty()
                ? List.of("")
                : lines;
    }

    private Color resolveTokenColor(
            JsonNode designTokens,
            String token,
            Color fallback
    ) {
        if (
                token == null
                        || token.isBlank()
        ) {
            return fallback;
        }

        String value =
                designTokens
                        .path("colors")
                        .path(token)
                        .asText(null);

        return parseColor(
                value,
                fallback
        );
    }

    /**
     * #RRGGBB
     * #RRGGBBAA
     * rgb(...)
     * rgba(...)
     *
     * 를 지원합니다.
     *
     * Color.TRANSPARENT는 Java에 존재하지 않으므로
     * 직접 alpha 0으로 생성합니다.
     */
    private Color parseColor(
            String value,
            Color fallback
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            return fallback;
        }

        String normalized =
                value.trim();

        try {
            /*
             * #RRGGBB
             */
            if (normalized.startsWith("#")) {
                String hex =
                        normalized.substring(1);

                if (hex.length() == 6) {
                    return new Color(
                            Integer.parseInt(
                                    hex.substring(0, 2),
                                    16
                            ),
                            Integer.parseInt(
                                    hex.substring(2, 4),
                                    16
                            ),
                            Integer.parseInt(
                                    hex.substring(4, 6),
                                    16
                            )
                    );
                }

                /*
                 * #RRGGBBAA
                 */
                if (hex.length() == 8) {
                    return new Color(
                            Integer.parseInt(
                                    hex.substring(0, 2),
                                    16
                            ),
                            Integer.parseInt(
                                    hex.substring(2, 4),
                                    16
                            ),
                            Integer.parseInt(
                                    hex.substring(4, 6),
                                    16
                            ),
                            Integer.parseInt(
                                    hex.substring(6, 8),
                                    16
                            )
                    );
                }
            }

            /*
             * rgb(...)
             */
            if (
                    normalized
                            .toLowerCase(Locale.ROOT)
                            .startsWith("rgb(")
            ) {
                String[] parts =
                        normalized
                                .substring(
                                        4,
                                        normalized.length() - 1
                                )
                                .split(",");

                if (parts.length == 3) {
                    return new Color(
                            Integer.parseInt(
                                    parts[0].trim()
                            ),
                            Integer.parseInt(
                                    parts[1].trim()
                            ),
                            Integer.parseInt(
                                    parts[2].trim()
                            )
                    );
                }
            }

            /*
             * rgba(...)
             */
            if (
                    normalized
                            .toLowerCase(Locale.ROOT)
                            .startsWith("rgba(")
            ) {
                String[] parts =
                        normalized
                                .substring(
                                        5,
                                        normalized.length() - 1
                                )
                                .split(",");

                if (parts.length == 4) {
                    int red =
                            Integer.parseInt(
                                    parts[0].trim()
                            );

                    int green =
                            Integer.parseInt(
                                    parts[1].trim()
                            );

                    int blue =
                            Integer.parseInt(
                                    parts[2].trim()
                            );

                    double alpha =
                            Double.parseDouble(
                                    parts[3].trim()
                            );

                    return new Color(
                            red,
                            green,
                            blue,
                            (int) Math.round(
                                    Math.max(
                                            0,
                                            Math.min(
                                                    1,
                                                    alpha
                                            )
                                    ) * 255
                            )
                    );
                }
            }

        } catch (RuntimeException ignored) {
            // fallback
        }

        return fallback;
    }
}
