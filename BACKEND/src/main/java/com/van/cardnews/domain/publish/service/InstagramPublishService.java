package com.van.cardnews.domain.publish.service;

import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.repository.ContentRepository;
import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import com.van.cardnews.domain.generatedimage.repository.GeneratedCardImageRepository;
import com.van.cardnews.domain.instagram.service.InstagramTokenService;
import com.van.cardnews.domain.publish.entity.PublishRecord;
import com.van.cardnews.domain.publish.repository.PublishRecordRepository;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import com.van.cardnews.global.instagram.InstagramCredentials;
import com.van.cardnews.global.publish.instagram.InstagramClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 인스타그램 캐러셀 발행의 순서 제어를 담당합니다.
 *
 * 자식 컨테이너 → 캐러셀 컨테이너 → 상태 폴링 → 발행 → permalink 조회 → publish_records 기록.
 *
 * 폴링을 이 서비스가 들고 있는 이유는 두 가지입니다.
 * 하나는 dev 프로필에서도 Mock을 상대로 폴링 루프가 실제로 돌게 하려는 것이고,
 * 다른 하나는 Cloud Run이 응답 이후 CPU를 주지 않아 <b>요청 스레드 안에서</b> 끝내야 하기 때문입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramPublishService {

    public static final String CHANNEL = "INSTAGRAM";

    private final ContentRepository contentRepository;
    private final GeneratedCardImageRepository generatedCardImageRepository;
    private final PublishRecordRepository publishRecordRepository;
    private final InstagramTokenService instagramTokenService;
    private final InstagramClient instagramClient;

    /** Meta 권장치. Higgsfield의 2초 간격을 복사하면 과호출이 된다. */
    @Value("${app.publish.instagram.poll-interval-ms}")
    private long pollIntervalMs;

    @Value("${app.publish.instagram.max-poll-count}")
    private int maxPollCount;

    /**
     * 콘텐츠의 카드 이미지를 캐러셀 한 건으로 발행하고 결과를 기록합니다.
     *
     * 발행 실패는 예외로 올리지 않고 {@code FAILED}로 기록해 돌려줍니다.
     * 예외를 던지면 트랜잭션이 롤백되어 실패 기록 자체가 사라지고, 재시도(VAN-13)가 근거를 잃습니다.
     */
    @Transactional
    public PublishRecord publishNow(Long contentId, String caption) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));

        PublishRecord record = publishRecordRepository.save(
                PublishRecord.publishNow(content, CHANNEL, caption));
        record.markProcessing();

        try {
            publishCarousel(record, contentId, caption);
        } catch (Exception e) {
            record.markFailed(e.getMessage());
            log.error("인스타그램 발행 실패 — contentId={}, publishRecordId={}", contentId, record.getId(), e);
        }

        return record;
    }

    private void publishCarousel(PublishRecord record, Long contentId, String caption) {
        List<GeneratedCardImage> cards =
                generatedCardImageRepository.findByContent_IdOrderBySortOrderAsc(contentId);

        if (cards.isEmpty()) {
            throw new IllegalStateException("발행할 카드 이미지가 없습니다.");
        }
        if (cards.size() > InstagramClient.MAX_CAROUSEL_ITEMS) {
            throw new IllegalStateException(
                    "캐러셀은 최대 " + InstagramClient.MAX_CAROUSEL_ITEMS + "장입니다. 현재 " + cards.size() + "장");
        }

        InstagramCredentials credentials = instagramTokenService.current();

        List<String> childIds = new ArrayList<>();
        for (GeneratedCardImage card : cards) {
            // 대체 텍스트 생성은 VAN-8. 그때까지는 alt_text 없이 보낸다.
            childIds.add(instagramClient.createCarouselItem(credentials, card.getImageUrl(), null));
        }

        // 컨테이너는 생성 후 24시간에 만료되므로 재시도 때도 매번 새로 만든다(재사용 금지).
        String containerId = instagramClient.createCarouselContainer(credentials, childIds, caption);

        awaitFinished(credentials, containerId);

        String igMediaId = instagramClient.publishContainer(credentials, containerId);
        record.markSuccess(igMediaId, instagramClient.getPermalink(credentials, igMediaId));

        log.info("인스타그램 발행 완료 — contentId={}, igMediaId={}, 카드 {}장", contentId, igMediaId, cards.size());
    }

    /**
     * 컨테이너가 처리될 때까지 기다립니다.
     */
    private void awaitFinished(InstagramCredentials credentials, String containerId) {
        for (int attempt = 1; attempt <= maxPollCount; attempt++) {
            InstagramClient.ContainerStatus status =
                    instagramClient.getContainerStatus(credentials, containerId);

            switch (status) {
                case FINISHED, PUBLISHED -> {
                    return;
                }
                case ERROR -> throw new IllegalStateException("컨테이너 처리가 실패했습니다 (ERROR).");
                case EXPIRED -> throw new IllegalStateException(
                        "컨테이너가 만료됐습니다 (EXPIRED). 생성 후 24시간이 지나면 재사용할 수 없습니다.");
                case IN_PROGRESS -> sleepBeforeNextPoll(attempt);
            }
        }

        throw new IllegalStateException(
                "컨테이너 처리가 " + maxPollCount + "회 폴링(" + pollIntervalMs + "ms 간격) 안에 끝나지 않았습니다.");
    }

    private void sleepBeforeNextPoll(int attempt) {
        if (attempt >= maxPollCount) {
            return;
        }

        try {
            Thread.sleep(pollIntervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("컨테이너 상태 폴링이 중단되었습니다.");
        }
    }
}
