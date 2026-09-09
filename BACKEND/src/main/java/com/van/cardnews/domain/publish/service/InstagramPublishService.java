package com.van.cardnews.domain.publish.service;

import com.van.cardnews.domain.approval.entity.ApprovalStatus;
import com.van.cardnews.domain.approval.repository.ApprovalRequestRepository;
import com.van.cardnews.domain.audit.entity.AuditAction;
import com.van.cardnews.domain.audit.service.AuditLogService;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.repository.ContentRepository;
import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import com.van.cardnews.domain.generatedimage.repository.GeneratedCardImageRepository;
import com.van.cardnews.domain.instagram.service.InstagramTokenService;
import com.van.cardnews.domain.jobhistory.entity.JobType;
import com.van.cardnews.domain.jobhistory.service.JobHistoryService;
import com.van.cardnews.domain.publish.entity.PublishRecord;
import com.van.cardnews.domain.publish.entity.PublishStatus;
import com.van.cardnews.domain.publish.repository.PublishRecordRepository;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import com.van.cardnews.global.instagram.InstagramCredentials;
import com.van.cardnews.global.publish.instagram.InstagramClient;
import com.van.cardnews.global.publish.instagram.InstagramPublishException;
import com.van.cardnews.global.publish.instagram.PublishFailure;
import com.van.cardnews.global.time.KoreaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    /** 아직 결과가 확정되지 않은 상태 — 이 중 하나라도 있으면 새 요청을 받지 않는다. */
    private static final List<PublishStatus> ACTIVE_STATUSES =
            List.of(PublishStatus.SCHEDULED, PublishStatus.PENDING, PublishStatus.PROCESSING);

    private final ContentRepository contentRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final GeneratedCardImageRepository generatedCardImageRepository;
    private final PublishRecordRepository publishRecordRepository;
    private final InstagramTokenService instagramTokenService;
    private final InstagramClient instagramClient;
    private final PublishPreflightValidator preflightValidator;
    private final AuditLogService auditLogService;
    private final JobHistoryService jobHistoryService;
    private final PublishTextComposer textComposer;

    /** Meta 권장치. Higgsfield의 2초 간격을 복사하면 과호출이 된다. */
    @Value("${app.publish.instagram.poll-interval-ms}")
    private long pollIntervalMs;

    @Value("${app.publish.instagram.max-poll-count}")
    private int maxPollCount;

    /** 예약은 워커 주기보다 넉넉히 앞서야 한다. 주기보다 짧으면 등록되자마자 유예 판정을 받는다. */
    @Value("${app.publish.schedule.min-lead-minutes}")
    private long minLeadMinutes;

    @Value("${app.publish.schedule.max-horizon-days}")
    private long maxHorizonDays;

    /** 자동 재시도 상한. 넘기면 사용자가 다시 요청해야 한다. */
    @Value("${app.publish.retry.max-count}")
    private int maxRetryCount;

    /**
     * 발행을 큐에 등록합니다. 실제 호출은 워커가 {@link #execute(Long)}로 수행합니다.
     *
     * 즉시 발행도 {@code scheduled_at = now()}인 예약 발행으로 통합했습니다.
     * Cloud Run은 요청 처리 중이 아니면 CPU를 주지 않아 {@code @Async}로 5분짜리 폴링을
     * 돌릴 수 없고, 경로를 하나로 두어야 검증·이력·재시도가 갈라지지 않습니다.
     */
    @Transactional
    public PublishRecord enqueue(Long contentId, String caption, String actorId) {
        return enqueueAt(contentId, caption, KoreaTime.now(), actorId);
    }

    /**
     * 지정한 시각으로 예약 등록합니다.
     *
     * 컨테이너는 생성 후 24시간에 만료되므로 여기서 미리 만들어 두지 않습니다. 등록되는 것은 큐 한 줄뿐이고,
     * 자식 컨테이너부터의 모든 호출은 워커가 도래 시점에 수행합니다.
     *
     * 캡션은 이 시점의 값으로 고정 보관합니다. 예약 후 발행 전에 콘텐츠가 수정돼도 의도한 문구가 나갑니다.
     */
    @Transactional
    public PublishRecord schedule(
            Long contentId, String caption, LocalDateTime scheduledAt, String actorId) {
        validateScheduledAt(scheduledAt);

        return enqueueAt(contentId, caption, scheduledAt, actorId);
    }

    /**
     * 발행 전 건을 취소합니다. 워커가 이미 선점한(PROCESSING) 건은 외부 호출이 진행 중이라 되돌릴 수 없습니다.
     */
    @Transactional
    public PublishRecord cancel(Long contentId) {
        PublishRecord record = latest(contentId);

        if (record.getStatus() == PublishStatus.SUCCESS) {
            throw new CustomException(ErrorCode.CONTENT_ALREADY_PUBLISHED);
        }
        if (record.getStatus() == PublishStatus.PROCESSING) {
            throw new CustomException(ErrorCode.PUBLISH_ALREADY_PROCESSING);
        }

        record.cancel();

        return record;
    }

    private PublishRecord enqueueAt(
            Long contentId, String caption, LocalDateTime scheduledAt, String actorId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));

        validateApproved(contentId);
        validateNotPublishedYet(contentId);

        List<GeneratedCardImage> cards =
                generatedCardImageRepository.findByContent_IdOrderBySortOrderAsc(contentId);

        if (cards.isEmpty()) {
            throw new CustomException(ErrorCode.CARD_IMAGES_NOT_READY);
        }

        // 캡션을 지정하지 않으면 미리보기에서 저장한 문구를, 그것도 없으면 카드 구성 결과에서 만든 문구를 쓴다.
        // 여기서 확정해 두어야 사전 검증이 실제로 나갈 문구를 보고, 예약 건도 등록 시점의 문구로 고정된다.
        String finalCaption = caption == null || caption.isBlank()
                ? caption(content)
                : caption;

        // Meta가 확실히 거절할 입력은 큐에 넣지 않는다. 넣으면 워커가 한도를 깎아 가며 재시도한다.
        preflightValidator.validate(cards, finalCaption);

        PublishRecord record = publishRecordRepository.save(
                PublishRecord.schedule(content, CHANNEL, finalCaption, scheduledAt));

        // 감사로그는 요청 시점에 남긴다. 실제 발행은 워커가 하므로 그때는 행위자를 알 수 없다.
        auditLogService.record(
                actorId,
                AuditAction.PUBLISH,
                contentId,
                "인스타그램 발행 요청 — 예약 시각 " + scheduledAt);

        return record;
    }

    /** 과거 시각과 최소 리드타임 미달은 같은 조건으로 걸린다. */
    private void validateScheduledAt(LocalDateTime scheduledAt) {
        if (scheduledAt == null) {
            throw new CustomException(ErrorCode.INVALID_SCHEDULE_TIME);
        }

        LocalDateTime now = KoreaTime.now();

        if (scheduledAt.isBefore(now.plusMinutes(minLeadMinutes))
                || scheduledAt.isAfter(now.plusDays(maxHorizonDays))) {
            throw new CustomException(ErrorCode.INVALID_SCHEDULE_TIME);
        }
    }

    /**
     * 큐에 등록된 발행 건을 실제로 실행합니다. 워커(VAN-11)가 도래한 건마다 호출합니다.
     *
     * 발행 실패는 예외로 올리지 않고 기록해 돌려줍니다.
     * 예외를 던지면 트랜잭션이 롤백되어 실패 기록 자체가 사라지고, 재시도가 근거를 잃습니다.
     *
     * 재시도 가능한 원인이면 {@link PublishRecord#fail}이 같은 행을 뒤로 재예약하므로
     * 워커의 다음 회차가 그대로 집어갑니다. 그때 자식 컨테이너부터 다시 만들므로
     * 만료된 컨테이너를 재사용하는 경로 자체가 없습니다.
     */
    @Transactional
    public PublishRecord execute(Long publishRecordId) {
        PublishRecord record = publishRecordRepository.findById(publishRecordId)
                .orElseThrow(() -> new CustomException(ErrorCode.PUBLISH_RECORD_NOT_FOUND));

        record.markProcessing();

        try {
            publishCarousel(record, record.getContent().getId(), record.getCaption());
        } catch (Exception e) {
            PublishFailure failure = classify(e);
            record.fail(failure, maxRetryCount);

            if (failure == PublishFailure.TOKEN_EXPIRED) {
                // 알림 채널이 아직 없어 error 로그가 유일한 신호다. 재시도로는 풀리지 않고 사람이 재인증해야 한다.
                log.error("인스타그램 토큰 만료로 발행 중단 — publishRecordId={}. 계정 관리자의 재인증이 필요하다.",
                        publishRecordId, e);
            } else {
                log.error("인스타그램 발행 실패 — publishRecordId={}, 원인={}, 재시도예약={}, 시도={}회",
                        publishRecordId, failure,
                        record.getStatus() == PublishStatus.SCHEDULED, record.getRetryCount(), e);
            }
        }

        return record;
    }

    /**
     * 예외를 실패 유형으로 옮깁니다.
     *
     * 클라이언트가 판정한 유형이 있으면 그대로 쓰고, 발행 전 자격 조회에서 걸린 토큰 만료도 같은 유형으로 모읍니다.
     * 그 외는 {@code UNKNOWN}이라 재시도 대상입니다 — 원인을 모른다고 발행을 포기하지는 않습니다.
     */
    private PublishFailure classify(Exception e) {
        if (e instanceof InstagramPublishException published) {
            return published.getFailure();
        }
        if (e instanceof CustomException custom
                && custom.getErrorCode() == ErrorCode.INSTAGRAM_TOKEN_NOT_FOUND) {
            return PublishFailure.TOKEN_EXPIRED;
        }

        return PublishFailure.UNKNOWN;
    }

    /**
     * 발행에 나갈 캡션입니다. 미리보기 화면이 발행 전에 보여 주는 문구가 이것입니다.
     *
     * 저장된 수정본이 없으면 매번 조립합니다. 저장하지 않는 한 콘텐츠를 고치면 캡션도 따라옵니다.
     */
    @Transactional(readOnly = true)
    public String caption(Long contentId) {
        return caption(contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND)));
    }

    private String caption(Content content) {
        String saved = content.getPublishCaption();

        return saved == null || saved.isBlank() ? textComposer.caption(content) : saved;
    }

    /**
     * 미리보기에서 고친 캡션을 저장합니다.
     *
     * 발행 시점이 아니라 여기서 검증하는 이유는, 상한을 넘긴 문구를 저장해 두면 사용자가
     * 발행 버튼을 누르는 순간에야 그 사실을 알게 되기 때문입니다.
     */
    @Transactional
    public String updateCaption(Long contentId, String caption) {
        preflightValidator.validateCaption(caption);

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));

        content.updatePublishCaption(caption);

        return caption;
    }

    /**
     * 최신 발행 건입니다. 예약(SCHEDULED)이든 완료(SUCCESS)든 화면은 이 한 건만 보면 됩니다.
     */
    @Transactional(readOnly = true)
    public PublishRecord latest(Long contentId) {
        return publishRecordRepository
                .findTopByContentIdAndChannelOrderByIdDesc(contentId, CHANNEL)
                .orElseThrow(() -> new CustomException(ErrorCode.PUBLISH_RECORD_NOT_FOUND));
    }

    /** 다운로드와 같은 정책 — 최신 승인요청이 APPROVED여야 한다. */
    private void validateApproved(Long contentId) {
        boolean approved = approvalRequestRepository
                .findTopByContentIdOrderByRequestedAtDesc(contentId)
                .map(request -> request.getStatus() == ApprovalStatus.APPROVED)
                .orElse(false);

        if (!approved) {
            throw new CustomException(ErrorCode.CONTENT_NOT_APPROVED_FOR_PUBLISH);
        }
    }

    /**
     * 같은 콘텐츠를 두 번 올리거나, 진행 중인 건 위에 겹쳐 요청하는 것을 막습니다.
     * SUCCESS 중복은 DB의 부분 유니크 인덱스도 막지만, 사용자에게는 500이 아니라 409로 답해야 합니다.
     */
    private void validateNotPublishedYet(Long contentId) {
        for (PublishRecord record : publishRecordRepository.findByContentIdOrderByIdDesc(contentId)) {
            if (!CHANNEL.equals(record.getChannel())) {
                continue;
            }
            if (record.getStatus() == PublishStatus.SUCCESS) {
                throw new CustomException(ErrorCode.CONTENT_ALREADY_PUBLISHED);
            }
            if (ACTIVE_STATUSES.contains(record.getStatus())) {
                throw new CustomException(ErrorCode.PUBLISH_ALREADY_REQUESTED);
            }
        }
    }

    private void publishCarousel(PublishRecord record, Long contentId, String caption) {
        List<GeneratedCardImage> cards =
                generatedCardImageRepository.findByContent_IdOrderBySortOrderAsc(contentId);

        if (cards.isEmpty()) {
            throw new InstagramPublishException(
                    PublishFailure.INVALID_CARDS, "발행할 카드 이미지가 없습니다.");
        }
        if (cards.size() > InstagramClient.MAX_CAROUSEL_ITEMS) {
            throw new InstagramPublishException(
                    PublishFailure.INVALID_CARDS,
                    "캐러셀은 최대 " + InstagramClient.MAX_CAROUSEL_ITEMS + "장입니다. 현재 " + cards.size() + "장");
        }

        InstagramCredentials credentials = instagramTokenService.current();

        // 소진된 상태에서 컨테이너를 만들면 한도만 더 깎고 실패한다. 등록 시점이 아니라 여기서 보는 이유는
        // 한도가 24시간 이동 윈도우라, 예약 등록 시점의 값이 발행 시점을 대변하지 못하기 때문이다.
        if (instagramClient.remainingQuota(credentials) <= 0) {
            throw new InstagramPublishException(
                    PublishFailure.RATE_LIMITED, "24시간 발행 한도가 남아 있지 않습니다.");
        }

        List<String> childIds = new ArrayList<>();
        for (GeneratedCardImage card : cards) {
            childIds.add(instagramClient.createCarouselItem(
                    credentials, card.getImageUrl(), textComposer.altText(record.getContent(), card)));
        }

        // 컨테이너는 생성 후 24시간에 만료되므로 재시도 때도 매번 새로 만든다(재사용 금지).
        String containerId = instagramClient.createCarouselContainer(credentials, childIds, caption);

        awaitFinished(credentials, containerId);

        String igMediaId = instagramClient.publishContainer(credentials, containerId);
        String permalink = instagramClient.getPermalink(credentials, igMediaId);
        record.markSuccess(igMediaId, permalink);

        // 성공 건만 작업이력에 남긴다. 실패까지 남기면 ContentService가 최신 작업 이력의 상태를
        // 콘텐츠 생성 상태로 내려보내므로(resolveGenerationStatus) 화면에 "생성 실패"로 뜨고,
        // PENDING/PROCESSING 이력은 콘텐츠 수정까지 막는다. 실패 원인은 publish_records에 남는다.
        jobHistoryService.createJobHistory(record.getContent(), JobType.INSTAGRAM_PUBLISH)
                .markCompleted(permalink);

        // 다운로드는 더 이상 PUBLISHED로 전이시키지 않는다. 실제 외부 발행만 이 상태를 만든다.
        record.getContent().publish();

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
                case ERROR -> throw new InstagramPublishException(
                        PublishFailure.CONTAINER_ERROR, "컨테이너 처리가 실패했습니다 (ERROR).");
                case EXPIRED -> throw new InstagramPublishException(
                        PublishFailure.CONTAINER_EXPIRED,
                        "컨테이너가 만료됐습니다 (EXPIRED). 생성 후 24시간이 지나면 재사용할 수 없습니다.");
                case IN_PROGRESS -> sleepBeforeNextPoll(attempt);
            }
        }

        throw new InstagramPublishException(
                PublishFailure.UNKNOWN,
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
