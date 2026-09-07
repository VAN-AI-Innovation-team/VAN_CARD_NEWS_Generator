package com.van.cardnews.domain.publish.service;

import com.sun.net.httpserver.HttpServer;
import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import com.van.cardnews.global.storage.ImageStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 발행 사전 검증 — 목업 데이터는 항상 통과하므로 규격 분기는 <b>합성 위반 입력</b>으로만 실행된다.
 *
 * 도달성 검증은 실제 HTTP HEAD라, JDK 내장 {@link HttpServer}를 띄워 200/404를 그대로 만든다.
 * 여기를 목킹하면 "우리가 읽을 수 있다"와 "Meta가 가져갈 수 있다"를 다시 뒤섞게 된다.
 */
class PublishPreflightValidatorTest {

    private HttpServer server;
    private String baseUrl;
    private Map<String, byte[]> storedBytes;
    private PublishPreflightValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        storedBytes = new HashMap<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int status = exchange.getRequestURI().getPath().startsWith("/missing") ? 404 : 200;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        ImageStorageService imageStorageService = mock(ImageStorageService.class);
        when(imageStorageService.read(anyString()))
                .thenAnswer(invocation -> storedBytes.get(invocation.<String>getArgument(0)));

        validator = new PublishPreflightValidator(imageStorageService);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    // ------------------------------------------------------------------
    // 카드 수
    // ------------------------------------------------------------------

    @Test
    void 목업_기본값인_카드_3장은_통과한다() {
        List<GeneratedCardImage> cards = cards(1080, 1080, 1080, 1080, 1080, 1080);

        assertThatCode(() -> validator.validate(cards, "캡션 #카드뉴스 @van")).doesNotThrowAnyException();
    }

    @Test
    void 카드가_11장이면_거절한다() {
        int[] sizes = new int[22];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = 1080;
        }

        assertThatThrownBy(() -> validator.validate(cards(sizes), "캡션"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("11장");
        assertThat(errorCodeOf(() -> validator.validate(cards(sizes), "캡션")))
                .isEqualTo(ErrorCode.INVALID_CARD_COUNT);
    }

    @Test
    void 카드가_1장이면_캐러셀이_아니라_거절한다() {
        assertThat(errorCodeOf(() -> validator.validate(cards(1080, 1080), "캡션")))
                .isEqualTo(ErrorCode.INVALID_CARD_COUNT);
    }

    // ------------------------------------------------------------------
    // 캡션
    // ------------------------------------------------------------------

    @Test
    void 캡션이_2200자를_넘으면_거절한다() {
        List<GeneratedCardImage> cards = cards(1080, 1080, 1080, 1080);

        assertThat(errorCodeOf(() -> validator.validate(cards, "가".repeat(2201))))
                .isEqualTo(ErrorCode.INVALID_CAPTION);
        assertThatCode(() -> validator.validate(cards, "가".repeat(2200))).doesNotThrowAnyException();
    }

    @Test
    void 해시태그가_30개를_넘으면_거절한다() {
        List<GeneratedCardImage> cards = cards(1080, 1080, 1080, 1080);

        assertThat(errorCodeOf(() -> validator.validate(cards, "#태그 ".repeat(31))))
                .isEqualTo(ErrorCode.INVALID_CAPTION);
        assertThatCode(() -> validator.validate(cards, "#태그 ".repeat(30))).doesNotThrowAnyException();
    }

    @Test
    void 멘션이_20개를_넘으면_거절한다() {
        List<GeneratedCardImage> cards = cards(1080, 1080, 1080, 1080);

        assertThat(errorCodeOf(() -> validator.validate(cards, "@van ".repeat(21))))
                .isEqualTo(ErrorCode.INVALID_CAPTION);
        assertThatCode(() -> validator.validate(cards, "@van ".repeat(20))).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // 이미지 규격 — 생성 경로가 보장하므로 합성 입력으로만 실행되는 분기
    // ------------------------------------------------------------------

    @Test
    void 폭이_1440을_넘으면_거절한다() {
        assertThat(errorCodeOf(() -> validator.validate(cards(1080, 1080, 2048, 2048), "캡션")))
                .isEqualTo(ErrorCode.INVALID_PUBLISH_IMAGE);
    }

    @Test
    void 폭이_320보다_작으면_거절한다() {
        assertThat(errorCodeOf(() -> validator.validate(cards(1080, 1080, 200, 200), "캡션")))
                .isEqualTo(ErrorCode.INVALID_PUBLISH_IMAGE);
    }

    @Test
    void 비율이_3대1이면_거절한다() {
        assertThat(errorCodeOf(() -> validator.validate(cards(1080, 1080, 1080, 360), "캡션")))
                .isEqualTo(ErrorCode.INVALID_PUBLISH_IMAGE);
    }

    @Test
    void 용량이_8MB를_넘으면_거절한다() {
        List<GeneratedCardImage> cards = cards(1080, 1080, 1080, 1080);
        storedBytes.put(cards.get(1).getImageUrl(), new byte[9 * 1024 * 1024]);

        assertThat(errorCodeOf(() -> validator.validate(cards, "캡션")))
                .isEqualTo(ErrorCode.INVALID_PUBLISH_IMAGE);
    }

    /** 캐러셀은 첫 장 비율로 전 슬라이드를 크롭한다 — 각 장이 규격 안이어도 섞이면 뒤 카드가 잘린다. */
    @Test
    void 카드_비율이_섞이면_각_장이_규격_안이어도_거절한다() {
        assertThat(errorCodeOf(() -> validator.validate(cards(1080, 1080, 1080, 1350), "캡션")))
                .isEqualTo(ErrorCode.INVALID_PUBLISH_IMAGE);
    }

    @Test
    void 이미지로_해석되지_않는_바이트는_거절한다() {
        List<GeneratedCardImage> cards = cards(1080, 1080, 1080, 1080);
        storedBytes.put(cards.get(0).getImageUrl(), "이건 이미지가 아니다".getBytes());

        assertThat(errorCodeOf(() -> validator.validate(cards, "캡션")))
                .isEqualTo(ErrorCode.INVALID_PUBLISH_IMAGE);
    }

    // ------------------------------------------------------------------
    // 외부 도달성
    // ------------------------------------------------------------------

    @Test
    void 외부에서_200이_아닌_URL은_거절한다() {
        List<GeneratedCardImage> cards = cards(1080, 1080, 1080, 1080);
        List<GeneratedCardImage> broken = List.of(
                cards.get(0),
                card(1, baseUrl + "/missing/card-1.png", 1080, 1080));

        assertThat(errorCodeOf(() -> validator.validate(broken, "캡션")))
                .isEqualTo(ErrorCode.PUBLISH_IMAGE_UNREACHABLE);
    }

    @Test
    void 연결_자체가_안_되는_URL은_거절한다() {
        List<GeneratedCardImage> cards = List.of(
                card(0, "http://127.0.0.1:1/card-0.png", 1080, 1080),
                card(1, "http://127.0.0.1:1/card-1.png", 1080, 1080));

        assertThat(errorCodeOf(() -> validator.validate(cards, "캡션")))
                .isEqualTo(ErrorCode.PUBLISH_IMAGE_UNREACHABLE);
    }

    // ------------------------------------------------------------------
    // 헬퍼
    // ------------------------------------------------------------------

    /** 폭·높이 쌍을 나열한 만큼 카드를 만들고, 각 카드의 실제 PNG 바이트를 스토리지에 넣는다. */
    private List<GeneratedCardImage> cards(int... widthHeightPairs) {
        List<GeneratedCardImage> cards = new ArrayList<>();

        for (int i = 0; i < widthHeightPairs.length / 2; i++) {
            int width = widthHeightPairs[i * 2];
            int height = widthHeightPairs[i * 2 + 1];
            GeneratedCardImage card = card(i, baseUrl + "/card-" + i + ".png", width, height);

            storedBytes.put(card.getImageUrl(), png(width, height));
            cards.add(card);
        }

        return cards;
    }

    private GeneratedCardImage card(int sortOrder, String imageUrl, int width, int height) {
        return GeneratedCardImage.create(
                null,
                GeneratedCardImage.CardType.CONTENT,
                sortOrder,
                sortOrder,
                imageUrl,
                width,
                height,
                null);
    }

    private byte[] png(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", output);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        return output.toByteArray();
    }

    private ErrorCode errorCodeOf(Runnable call) {
        try {
            call.run();
        } catch (CustomException e) {
            return e.getErrorCode();
        }

        throw new AssertionError("거절되지 않았다.");
    }
}
