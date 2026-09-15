package com.van.cardnews.domain.publish.service;

import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import com.van.cardnews.global.image.ImageBytes;
import com.van.cardnews.global.publish.instagram.InstagramClient;
import com.van.cardnews.global.storage.ImageStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 발행 요청을 큐에 넣기 전에 Meta가 확실히 거절할 입력을 걸러냅니다.
 *
 * 여기서 걸리는 것은 두 종류입니다.
 * 하나는 <b>요청의 문제</b>(카드 수, 캡션)로 사용자가 콘텐츠를 고쳐야 풀립니다.
 * 다른 하나는 <b>생성 경로의 문제</b>(이미지 규격, URL 도달성)로, 생성 시점에 이미 규격이 맞춰지므로
 * 여기서 걸린다는 것 자체가 생성이나 저장소 공개 설정이 깨졌다는 신호입니다. 메시지도 그렇게 구분합니다.
 *
 * 쿼터는 여기서 보지 않습니다. 24시간 이동 윈도우라 등록 시점 값이 발행 시점을 대변하지 못하고,
 * 여기서 막으면 일주일 뒤 예약이 지금의 쿼터 때문에 거절됩니다.
 * 발행 직전 확인은 {@code InstagramPublishService}가 합니다.
 */
@Slf4j
@Component
public class PublishPreflightValidator {

    /** 캐러셀 하한. 1장은 캐러셀이 아니라 단일 이미지 게시물이고, 이 경로는 캐러셀만 만든다. */
    static final int MIN_CARDS = 2;

    static final int MAX_CAPTION_LENGTH = 2200;
    static final int MAX_HASHTAGS = 30;
    static final int MAX_MENTIONS = 20;

    static final int MIN_IMAGE_WIDTH = 320;
    static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;

    /** 세로 4:5 ~ 가로 1.91:1. Meta가 받는 비율 범위입니다. */
    static final double MIN_ASPECT_RATIO = 4.0 / 5.0;
    static final double MAX_ASPECT_RATIO = 1.91;

    /** 렌더링 반올림으로 1~2px 어긋나는 것까지 잡으면 정상 카드가 걸린다. */
    private static final double ASPECT_RATIO_TOLERANCE = 0.01;

    private static final Pattern HASHTAG = Pattern.compile("#[\\p{L}\\p{N}_]+");
    private static final Pattern MENTION = Pattern.compile("@[\\p{L}\\p{N}_.]+");

    private final ImageStorageService imageStorageService;
    private final HttpClient httpClient;

    public PublishPreflightValidator(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 위반이 있으면 첫 위반에서 400으로 던집니다. 전부 모아 보고하지 않는 이유는,
     * 카드 수가 틀린 상태에서 이미지 10장을 내려받아 검사하는 것이 낭비이기 때문입니다.
     */
    public void validate(List<GeneratedCardImage> cards, String caption) {
        validateCardCount(cards);
        validateCaption(caption);
        validateImages(cards);
    }

    private void validateCardCount(List<GeneratedCardImage> cards) {
        if (cards.size() < MIN_CARDS || cards.size() > InstagramClient.MAX_CAROUSEL_ITEMS) {
            throw new CustomException(
                    ErrorCode.INVALID_CARD_COUNT,
                    "인스타그램 캐러셀은 " + MIN_CARDS + "~" + InstagramClient.MAX_CAROUSEL_ITEMS
                            + "장이어야 합니다. 현재 " + cards.size() + "장입니다.");
        }
    }

    /** 캡션만 따로 봅니다. 미리보기의 저장이 발행까지 가지 않고 여기서 걸러지도록. */
    public void validateCaption(String caption) {
        if (caption == null || caption.isBlank()) {
            return;
        }

        if (caption.length() > MAX_CAPTION_LENGTH) {
            throw new CustomException(
                    ErrorCode.INVALID_CAPTION,
                    "캡션은 " + MAX_CAPTION_LENGTH + "자 이하여야 합니다. 현재 " + caption.length() + "자입니다.");
        }

        long hashtags = HASHTAG.matcher(caption).results().count();
        if (hashtags > MAX_HASHTAGS) {
            throw new CustomException(
                    ErrorCode.INVALID_CAPTION,
                    "해시태그는 " + MAX_HASHTAGS + "개 이하여야 합니다. 현재 " + hashtags + "개입니다.");
        }

        long mentions = MENTION.matcher(caption).results().count();
        if (mentions > MAX_MENTIONS) {
            throw new CustomException(
                    ErrorCode.INVALID_CAPTION,
                    "@ 멘션은 " + MAX_MENTIONS + "개 이하여야 합니다. 현재 " + mentions + "개입니다.");
        }
    }

    /**
     * 이미지는 <b>실제 바이트</b>로 판정합니다.
     *
     * {@code generated_card_images.resolution_width/height}는 기록 시점의 사본이고,
     * 이 방어선이 잡아야 할 것이 바로 기록과 실물이 어긋나는 경우입니다. DB를 믿으면 자기 자신을 검증하게 됩니다.
     */
    private void validateImages(List<GeneratedCardImage> cards) {
        double firstRatio = 0;

        for (GeneratedCardImage card : cards) {
            requireReachable(card.getImageUrl());

            byte[] bytes = imageStorageService.read(card.getImageUrl());

            if (bytes.length > MAX_IMAGE_BYTES) {
                throw invalidImage(card, bytes.length / 1024 / 1024 + "MB로 8MB를 넘습니다");
            }

            BufferedImage image = decode(card, bytes);
            double ratio = (double) image.getWidth() / image.getHeight();

            if (image.getWidth() < MIN_IMAGE_WIDTH || image.getWidth() > ImageBytes.MAX_WIDTH) {
                throw invalidImage(card, "폭이 " + image.getWidth() + "px로 "
                        + MIN_IMAGE_WIDTH + "~" + ImageBytes.MAX_WIDTH + "px 범위를 벗어납니다");
            }
            if (ratio < MIN_ASPECT_RATIO - ASPECT_RATIO_TOLERANCE
                    || ratio > MAX_ASPECT_RATIO + ASPECT_RATIO_TOLERANCE) {
                throw invalidImage(card, "비율이 " + image.getWidth() + ":" + image.getHeight()
                        + "로 4:5 ~ 1.91:1 범위를 벗어납니다");
            }

            // 캐러셀은 첫 장 비율로 전 슬라이드를 크롭한다. 섞이면 뒤 카드의 내용이 잘려 나간다.
            if (firstRatio == 0) {
                firstRatio = ratio;
            } else if (Math.abs(ratio - firstRatio) > ASPECT_RATIO_TOLERANCE) {
                throw invalidImage(card, "비율이 첫 카드와 다릅니다. "
                        + "캐러셀은 첫 장 비율로 전 슬라이드가 크롭되므로 카드 비율이 같아야 합니다");
            }
        }
    }

    /**
     * Meta가 이미지를 직접 가져가므로, 우리가 읽을 수 있다는 것은 근거가 되지 못합니다.
     * {@code ImageStorageService.read}는 GCS SDK·로컬 파일로 읽어 공개 URL의 도달성과 무관합니다.
     */
    private void requireReachable(String imageUrl) {
        int status;

        try {
            status = httpClient.send(
                    HttpRequest.newBuilder(URI.create(imageUrl))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .timeout(Duration.ofSeconds(10))
                            .build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("카드 이미지 URL 확인이 중단되었습니다.");
        } catch (Exception e) {
            log.warn("카드 이미지 URL 확인 실패 — {} ({})", imageUrl, e.getClass().getSimpleName());

            throw new CustomException(
                    ErrorCode.PUBLISH_IMAGE_UNREACHABLE,
                    "카드 이미지 URL에 접근할 수 없습니다: " + imageUrl);
        }

        if (status != 200) {
            throw new CustomException(
                    ErrorCode.PUBLISH_IMAGE_UNREACHABLE,
                    "카드 이미지 URL이 " + status + "를 반환했습니다: " + imageUrl);
        }
    }

    private BufferedImage decode(GeneratedCardImage card, byte[] bytes) {
        BufferedImage image;

        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw invalidImage(card, "이미지로 해석할 수 없습니다");
        }

        if (image == null) {
            throw invalidImage(card, "이미지로 해석할 수 없습니다");
        }

        return image;
    }

    private CustomException invalidImage(GeneratedCardImage card, String reason) {
        return new CustomException(
                ErrorCode.INVALID_PUBLISH_IMAGE,
                (card.getSortOrder() + 1) + "번째 카드 이미지의 " + reason
                        + ". 카드 이미지를 다시 생성해 주세요.");
    }
}
