package com.van.cardnews.domain.publish.service;

import com.van.cardnews.domain.approval.entity.ApprovalRequest;
import com.van.cardnews.domain.approval.entity.ApprovalStatus;
import com.van.cardnews.domain.approval.repository.ApprovalRequestRepository;
import com.van.cardnews.domain.audit.entity.AuditAction;
import com.van.cardnews.domain.audit.service.AuditLogService;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.repository.ContentRepository;
import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import com.van.cardnews.domain.generatedimage.repository.GeneratedCardImageRepository;
import com.van.cardnews.domain.instagram.service.InstagramTokenService;
import com.van.cardnews.domain.jobhistory.entity.JobHistory;
import com.van.cardnews.domain.jobhistory.entity.JobStatus;
import com.van.cardnews.domain.jobhistory.entity.JobType;
import com.van.cardnews.domain.jobhistory.service.JobHistoryService;
import com.van.cardnews.domain.publish.entity.PublishRecord;
import com.van.cardnews.domain.publish.entity.PublishStatus;
import com.van.cardnews.domain.publish.repository.PublishRecordRepository;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import com.van.cardnews.global.instagram.InstagramCredentials;
import com.van.cardnews.global.publish.instagram.MockInstagramClient;
import com.van.cardnews.global.publish.instagram.PublishFailure;
import com.van.cardnews.global.time.KoreaTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dev 프로필 기준 — MockInstagramClient로 승인 게이트·큐 등록·발행 실행을 검증한다.
 *
 * 실계정 전환(VAN-18) 전까지 이 에픽은 목업으로만 검증되므로 호출 순서·횟수·상태 전이까지 단언한다.
 * 스케줄러가 큐를 자동으로 집어가는 부분은 VAN-11이라, 여기서는 테스트가 실행 경로를 직접 몬다.
 */
class InstagramPublishServiceTest {

    private static final long CONTENT_ID = 42L;
    private static final String ACTOR_ID = "hanms";
    private static final long RECORD_ID = 7L;
    private static final int MAX_POLL_COUNT = 3;
    private static final long MIN_LEAD_MINUTES = 5;
    private static final long MAX_HORIZON_DAYS = 30;
    private static final int MAX_RETRY_COUNT = 2;

    private Content content;
    private List<GeneratedCardImage> cards;
    private List<PublishRecord> savedRecords;
    private ApprovalStatus approvalStatus;
    private MockInstagramClient instagramClient;
    private InstagramTokenService tokenService;
    private PublishPreflightValidator preflightValidator;
    private AuditLogService auditLogService;
    private JobHistoryService jobHistoryService;
    private List<JobHistory> jobHistories;
    private InstagramPublishService service;

    @BeforeEach
    void setUp() {
        content = mock(Content.class);
        lenient().when(content.getId()).thenReturn(CONTENT_ID);

        cards = new ArrayList<>();
        savedRecords = new ArrayList<>();
        approvalStatus = ApprovalStatus.APPROVED;
        instagramClient = new MockInstagramClient();

        ContentRepository contentRepository = mock(ContentRepository.class);
        lenient().when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(content));

        ApprovalRequest approvalRequest = mock(ApprovalRequest.class);
        lenient().when(approvalRequest.getStatus()).thenAnswer(invocation -> approvalStatus);
        ApprovalRequestRepository approvalRequestRepository = mock(ApprovalRequestRepository.class);
        lenient().when(approvalRequestRepository.findTopByContentIdOrderByRequestedAtDesc(anyLong()))
                .thenReturn(Optional.of(approvalRequest));

        GeneratedCardImageRepository cardImageRepository = mock(GeneratedCardImageRepository.class);
        lenient().when(cardImageRepository.findByContent_IdOrderBySortOrderAsc(anyLong()))
                .thenAnswer(invocation -> cards);
        lenient().when(cardImageRepository.countByContent_Id(anyLong()))
                .thenAnswer(invocation -> (long) cards.size());

        PublishRecordRepository publishRecordRepository = mock(PublishRecordRepository.class);
        lenient().when(publishRecordRepository.save(any(PublishRecord.class)))
                .thenAnswer(invocation -> {
                    PublishRecord record = invocation.getArgument(0);
                    savedRecords.add(record);
                    return record;
                });
        lenient().when(publishRecordRepository.findById(anyLong()))
                .thenAnswer(invocation -> savedRecords.isEmpty()
                        ? Optional.empty()
                        : Optional.of(savedRecords.get(savedRecords.size() - 1)));
        lenient().when(publishRecordRepository.findByContentIdOrderByIdDesc(anyLong()))
                .thenAnswer(invocation -> {
                    List<PublishRecord> reversed = new ArrayList<>(savedRecords);
                    Collections.reverse(reversed);
                    return reversed;
                });

        tokenService = mock(InstagramTokenService.class);
        lenient().when(tokenService.current())
                .thenReturn(new InstagramCredentials("dev-ig-user", "dev-dummy-ig-long-lived-token"));

        // 규격 규칙 자체는 PublishPreflightValidatorTest가 본다. 여기서 보는 것은 호출 순서다 —
        // 검증이 저장·Meta 호출보다 먼저인가.
        preflightValidator = mock(PublishPreflightValidator.class);

        auditLogService = mock(AuditLogService.class);

        jobHistories = new ArrayList<>();
        jobHistoryService = mock(JobHistoryService.class);
        lenient().when(jobHistoryService.createJobHistory(any(), any()))
                .thenAnswer(invocation -> {
                    JobHistory history = JobHistory.createPending(
                            invocation.getArgument(0), invocation.getArgument(1));
                    jobHistories.add(history);
                    return history;
                });

        service = new InstagramPublishService(
                contentRepository,
                approvalRequestRepository,
                cardImageRepository,
                publishRecordRepository,
                tokenService,
                instagramClient,
                preflightValidator,
                auditLogService,
                jobHistoryService);

        ReflectionTestUtils.setField(service, "pollIntervalMs", 1L);
        ReflectionTestUtils.setField(service, "maxPollCount", MAX_POLL_COUNT);
        ReflectionTestUtils.setField(service, "minLeadMinutes", MIN_LEAD_MINUTES);
        ReflectionTestUtils.setField(service, "maxHorizonDays", MAX_HORIZON_DAYS);
        ReflectionTestUtils.setField(service, "maxRetryCount", MAX_RETRY_COUNT);
    }

    private void givenCards(int count) {
        for (int i = 0; i < count; i++) {
            cards.add(GeneratedCardImage.create(
                    content,
                    GeneratedCardImage.CardType.CONTENT,
                    i,
                    i,
                    "https://example.com/card-" + i + ".jpg",
                    1080,
                    1080,
                    null));
        }
    }

    private PublishRecord publish(String caption) {
        service.enqueue(CONTENT_ID, caption, ACTOR_ID);
        return service.execute(RECORD_ID);
    }

    private long callCount(String prefix) {
        return instagramClient.calls().stream().filter(call -> call.startsWith(prefix)).count();
    }

    // ------------------------------------------------------------------
    // 큐 등록 (승인 게이트)
    // ------------------------------------------------------------------

    @Test
    void 승인된_콘텐츠는_예약_상태로_큐에_등록된다() {
        givenCards(3);

        PublishRecord record = service.enqueue(CONTENT_ID, "캡션", ACTOR_ID);

        assertThat(record.getStatus()).isEqualTo(PublishStatus.SCHEDULED);
        assertThat(record.getScheduledAt()).isNotNull();
        assertThat(record.getChannel()).isEqualTo(InstagramPublishService.CHANNEL);
        assertThat(record.getCaption()).isEqualTo("캡션");
        // 등록 단계에서는 Meta를 부르지 않는다. 발행은 워커가 한다.
        assertThat(instagramClient.calls()).isEmpty();
    }

    @Test
    void 미승인_콘텐츠는_발행_전용_메시지로_거절된다() {
        givenCards(3);
        approvalStatus = ApprovalStatus.PENDING;

        assertThatThrownBy(() -> service.enqueue(CONTENT_ID, "캡션", ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.CONTENT_NOT_APPROVED_FOR_PUBLISH.getDefaultMessage())
                // 다운로드용 문구를 재사용하면 "다운로드할 수 있습니다"가 발행 화면에 뜬다
                .hasMessageContaining("발행");
    }

    @Test
    void 카드_이미지가_없으면_큐에_넣지_않는다() {
        assertThatThrownBy(() -> service.enqueue(CONTENT_ID, "캡션", ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.CARD_IMAGES_NOT_READY.getDefaultMessage());
    }

    @Test
    void 사전_검증에_걸리면_큐에_넣지_않고_Meta도_부르지_않는다() {
        givenCards(11);
        doThrow(new CustomException(ErrorCode.INVALID_CARD_COUNT))
                .when(preflightValidator).validate(any(), any());

        assertThatThrownBy(() -> service.enqueue(CONTENT_ID, "캡션", ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.INVALID_CARD_COUNT.getDefaultMessage());

        // 큐에 들어갔다면 워커가 한도를 깎아 가며 재시도한다. 등록 자체가 없어야 한다.
        assertThat(savedRecords).isEmpty();
        assertThat(instagramClient.calls()).isEmpty();
    }

    @Test
    void 이미_발행에_성공했으면_409로_거절한다() {
        givenCards(1);
        publish("캡션");

        assertThatThrownBy(() -> service.enqueue(CONTENT_ID, "캡션", ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.CONTENT_ALREADY_PUBLISHED.getDefaultMessage());
    }

    @Test
    void 진행_중인_건이_있으면_409로_거절한다() {
        givenCards(1);
        service.enqueue(CONTENT_ID, "캡션", ACTOR_ID);

        assertThatThrownBy(() -> service.enqueue(CONTENT_ID, "캡션", ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.PUBLISH_ALREADY_REQUESTED.getDefaultMessage());
    }

    @Test
    void 실패한_건_위에는_다시_요청할_수_있다() {
        givenCards(1);
        instagramClient.failAt(MockInstagramClient.Step.PUBLISH, PublishFailure.TOKEN_EXPIRED);
        assertThat(publish("캡션").getStatus()).isEqualTo(PublishStatus.FAILED);

        instagramClient.reset();

        assertThat(service.enqueue(CONTENT_ID, "캡션", ACTOR_ID).getStatus()).isEqualTo(PublishStatus.SCHEDULED);
    }

    @Test
    void 없는_콘텐츠는_거절한다() {
        assertThatThrownBy(() -> service.enqueue(999L, "캡션", ACTOR_ID))
                .isInstanceOf(CustomException.class);
    }

    // ------------------------------------------------------------------
    // 발행 실행
    // ------------------------------------------------------------------

    @Test
    void 카드_3장을_캐러셀로_발행하고_콘텐츠를_PUBLISHED로_전이한다() {
        givenCards(3);

        PublishRecord record = publish("테스트 캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.SUCCESS);
        assertThat(record.getIgMediaId()).startsWith("mock-media-");
        assertThat(record.getPermalink()).contains(record.getIgMediaId());
        assertThat(record.getPublishedAt()).isNotNull();
        assertThat(record.getErrorMessage()).isNull();
        verify(content).publish();
    }

    @Test
    void 자식_컨테이너부터_permalink까지_정해진_순서로_호출한다() {
        givenCards(2);

        publish("캡션");

        assertThat(instagramClient.calls()).containsExactly(
                "remainingQuota",
                "createCarouselItem:https://example.com/card-0.jpg",
                "createCarouselItem:https://example.com/card-1.jpg",
                "createCarouselContainer:2|caption",
                "getContainerStatus:mock-carousel-3",
                "getContainerStatus:mock-carousel-3",
                "publishContainer:mock-carousel-3",
                "getPermalink:mock-media-4");
    }

    /**
     * 쿼터는 등록 시점이 아니라 여기서 본다 — 24시간 이동 윈도우라 예약 등록 시점의 값이 발행 시점을 대변하지 못한다.
     * 소진 상태에서 컨테이너를 만들면 한도만 더 깎으므로 첫 호출에서 멈춰야 한다.
     */
    @Test
    void 쿼터가_소진되면_컨테이너를_만들지_않고_재시도로_돌린다() {
        givenCards(3);
        instagramClient.setRemainingQuota(0);

        PublishRecord record = publish("캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.SCHEDULED);
        assertThat(record.getFailureType()).isEqualTo(PublishFailure.RATE_LIMITED);
        assertThat(instagramClient.calls()).containsExactly("remainingQuota");
    }

    /** 폴링 루프가 dev에서도 실제로 도는지 — IN_PROGRESS 한 번을 거쳐 FINISHED에 닿아야 한다. */
    @Test
    void 컨테이너가_처리될_때까지_폴링한다() {
        givenCards(1);

        publish("캡션");

        assertThat(callCount("getContainerStatus")).isEqualTo(2);
    }

    @Test
    void 폴링이_끝나지_않으면_최대_횟수에서_실패로_기록한다() {
        givenCards(1);
        instagramClient.failAt(MockInstagramClient.Step.STATUS_NEVER_FINISH);

        PublishRecord record = publish("캡션");

        // 원인을 모르는 실패이므로 포기하지 않고 재예약한다
        assertThat(record.getStatus()).isEqualTo(PublishStatus.SCHEDULED);
        assertThat(record.getFailureType()).isEqualTo(PublishFailure.UNKNOWN);
        assertThat(callCount("getContainerStatus")).isEqualTo(MAX_POLL_COUNT);
        assertThat(callCount("publishContainer")).isZero();
    }

    @Test
    void 컨테이너가_만료되면_폴링을_멈추고_재시도로_넘긴다() {
        givenCards(1);
        instagramClient.failAt(MockInstagramClient.Step.STATUS_EXPIRED);

        PublishRecord record = publish("캡션");

        assertThat(record.getFailureType()).isEqualTo(PublishFailure.CONTAINER_EXPIRED);
        assertThat(record.getStatus()).isEqualTo(PublishStatus.SCHEDULED);
        assertThat(callCount("getContainerStatus")).isEqualTo(1);
    }

    @Test
    void 컨테이너가_ERROR면_실패로_기록한다() {
        givenCards(1);
        instagramClient.failAt(MockInstagramClient.Step.STATUS_ERROR);

        assertThat(publish("캡션").getFailureType()).isEqualTo(PublishFailure.CONTAINER_ERROR);
    }

    /**
     * 발행 실패를 예외로 올리면 트랜잭션이 롤백되어 실패 기록 자체가 사라진다.
     * 재시도(VAN-13)가 기댈 근거가 이 행이므로 예외 대신 FAILED로 남긴다.
     */
    @Test
    void 발행_단계_실패는_예외_대신_기록으로_남는다() {
        givenCards(2);
        instagramClient.failAt(MockInstagramClient.Step.PUBLISH, PublishFailure.TOKEN_EXPIRED);

        PublishRecord record = publish("캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getErrorMessage()).isNotBlank();
        assertThat(record.getIgMediaId()).isNull();
    }

    @Test
    void 카드가_11장이면_호출_전에_걸러낸다() {
        givenCards(11);

        PublishRecord record = publish("캡션");

        // 콘텐츠가 그대로인 한 다시 올려도 같은 결과라 재시도하지 않는다
        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getFailureType()).isEqualTo(PublishFailure.INVALID_CARDS);
        assertThat(instagramClient.calls()).isEmpty();
    }

    @Test
    void 없는_발행_건은_실행할_수_없다() {
        assertThatThrownBy(() -> service.execute(RECORD_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.PUBLISH_RECORD_NOT_FOUND.getDefaultMessage());
    }

    // ------------------------------------------------------------------
    // 실패 원인별 처리 (VAN-13)
    //
    // 이 네 가지 실패는 실 API로도 재현할 수 없다 — 컨테이너를 24시간 묵히거나, 하루 100건을 올리거나,
    // 토큰을 일부러 만료시킬 수 없다. Mock의 실패 주입이 유일한 검증 수단이고 전환 후에도 그렇다.
    // ------------------------------------------------------------------

    @Test
    void 토큰_만료는_재시도하지_않고_재인증을_요구하는_메시지로_멈춘다() {
        givenCards(1);
        instagramClient.failAt(MockInstagramClient.Step.CREATE_ITEM, PublishFailure.TOKEN_EXPIRED);

        PublishRecord record = publish("캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getRetryCount()).isZero();
        assertThat(record.getFailureType()).isEqualTo(PublishFailure.TOKEN_EXPIRED);
        assertThat(record.getErrorMessage()).contains("재인증");
    }

    /** 발행 도중이 아니라 자격 조회 단계에서 걸린 만료도 같은 유형으로 모아야 한다. */
    @Test
    void 자격_조회_단계의_토큰_만료도_같은_유형으로_분류한다() {
        givenCards(1);
        when(tokenService.current()).thenThrow(new CustomException(ErrorCode.INSTAGRAM_TOKEN_NOT_FOUND));

        PublishRecord record = publish("캡션");

        assertThat(record.getFailureType()).isEqualTo(PublishFailure.TOKEN_EXPIRED);
        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(instagramClient.calls()).isEmpty();
    }

    @Test
    void 레이트리밋은_즉시_재시도하지_않고_쿼터_회복까지_기다린다() {
        givenCards(1);
        instagramClient.failAt(MockInstagramClient.Step.PUBLISH, PublishFailure.RATE_LIMITED);
        LocalDateTime before = KoreaTime.now();

        PublishRecord record = publish("캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.SCHEDULED);
        assertThat(record.getRetryCount()).isEqualTo(1);
        assertThat(record.getScheduledAt())
                .isAfterOrEqualTo(before.plus(PublishFailure.RATE_LIMITED.getRetryDelay()));
    }

    @Test
    void 이미지를_가져가지_못하면_원인_확인_전까지_재시도하지_않는다() {
        givenCards(1);
        instagramClient.failAt(MockInstagramClient.Step.CREATE_ITEM, PublishFailure.IMAGE_UNREACHABLE);

        PublishRecord record = publish("캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getRetryCount()).isZero();
        assertThat(record.getErrorMessage()).contains("저장소");
    }

    /** 만료된 컨테이너는 되살릴 수 없다. 재시도는 자식 컨테이너부터 새로 만들어야 한다. */
    @Test
    void 컨테이너_만료로_재시도할_때_만료된_컨테이너를_재사용하지_않는다() {
        givenCards(2);
        instagramClient.failAt(MockInstagramClient.Step.STATUS_EXPIRED);
        publish("캡션");

        String expiredContainer = instagramClient.calls().stream()
                .filter(call -> call.startsWith("getContainerStatus:"))
                .findFirst()
                .orElseThrow()
                .substring("getContainerStatus:".length());

        instagramClient.reset();
        service.execute(RECORD_ID);

        assertThat(callCount("createCarouselItem")).isEqualTo(2);
        assertThat(callCount("createCarouselContainer")).isEqualTo(1);
        assertThat(instagramClient.calls()).noneMatch(call -> call.endsWith(expiredContainer));
    }

    @Test
    void 재시도_상한을_넘기면_더_이상_재예약하지_않는다() {
        givenCards(1);
        instagramClient.failAt(MockInstagramClient.Step.STATUS_ERROR);

        PublishRecord record = publish("캡션");

        for (int attempt = 1; attempt < MAX_RETRY_COUNT; attempt++) {
            assertThat(record.getStatus()).isEqualTo(PublishStatus.SCHEDULED);
            service.execute(RECORD_ID);
        }

        assertThat(record.getRetryCount()).isEqualTo(MAX_RETRY_COUNT);

        service.execute(RECORD_ID);

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getRetryCount()).isEqualTo(MAX_RETRY_COUNT);
    }

    // ------------------------------------------------------------------
    // 예약 등록·취소
    // ------------------------------------------------------------------

    @Test
    void 예약은_지정한_시각으로_큐에_등록되고_캡션이_고정된다() {
        givenCards(3);
        LocalDateTime scheduledAt = KoreaTime.now().plusMinutes(10);

        PublishRecord record = service.schedule(CONTENT_ID, "예약 캡션", scheduledAt, ACTOR_ID);

        assertThat(record.getStatus()).isEqualTo(PublishStatus.SCHEDULED);
        assertThat(record.getScheduledAt()).isEqualTo(scheduledAt);
        assertThat(record.getCaption()).isEqualTo("예약 캡션");
        // 컨테이너는 24시간에 만료되므로 등록 시점에 미리 만들어 두지 않는다
        assertThat(instagramClient.calls()).isEmpty();
    }

    @Test
    void 과거_시각_예약은_거절한다() {
        givenCards(3);

        assertThatThrownBy(() -> service.schedule(CONTENT_ID, "캡션", KoreaTime.now().minusMinutes(1), ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.INVALID_SCHEDULE_TIME.getDefaultMessage());
    }

    @Test
    void 최소_리드타임보다_가까운_예약은_거절한다() {
        givenCards(3);

        assertThatThrownBy(() -> service.schedule(
                CONTENT_ID, "캡션", KoreaTime.now().plusMinutes(MIN_LEAD_MINUTES - 1), ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.INVALID_SCHEDULE_TIME.getDefaultMessage());
    }

    @Test
    void 최대_예약_기간을_넘기면_거절한다() {
        givenCards(3);

        assertThatThrownBy(() -> service.schedule(
                CONTENT_ID, "캡션", KoreaTime.now().plusDays(MAX_HORIZON_DAYS).plusMinutes(1), ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.INVALID_SCHEDULE_TIME.getDefaultMessage());
    }

    @Test
    void 예약을_취소하면_CANCELED가_되고_같은_콘텐츠를_다시_예약할_수_있다() {
        givenCards(3);
        service.schedule(CONTENT_ID, "캡션", KoreaTime.now().plusMinutes(10), ACTOR_ID);

        assertThat(service.cancel(CONTENT_ID).getStatus()).isEqualTo(PublishStatus.CANCELED);
        assertThat(service.schedule(CONTENT_ID, "캡션", KoreaTime.now().plusMinutes(20), ACTOR_ID).getStatus())
                .isEqualTo(PublishStatus.SCHEDULED);
    }

    @Test
    void 워커가_선점한_건은_취소할_수_없다() {
        givenCards(3);
        service.schedule(CONTENT_ID, "캡션", KoreaTime.now().plusMinutes(10), ACTOR_ID).markProcessing();

        assertThatThrownBy(() -> service.cancel(CONTENT_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.PUBLISH_ALREADY_PROCESSING.getDefaultMessage());
    }

    @Test
    void 이미_발행된_건은_취소할_수_없다() {
        givenCards(1);
        publish("캡션");

        assertThatThrownBy(() -> service.cancel(CONTENT_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.CONTENT_ALREADY_PUBLISHED.getDefaultMessage());
    }

    // ------------------------------------------------------------------
    // 상태 조회
    // ------------------------------------------------------------------

    @Test
    void 최신_발행_건을_조회한다() {
        givenCards(1);
        publish("캡션");

        PublishRecord latest = service.latest(CONTENT_ID);

        assertThat(latest.getStatus()).isEqualTo(PublishStatus.SUCCESS);
        assertThat(latest.getPermalink()).isNotBlank();
    }

    @Test
    void 발행_이력이_없으면_조회에_실패한다() {
        assertThatThrownBy(() -> service.latest(CONTENT_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.PUBLISH_RECORD_NOT_FOUND.getDefaultMessage());
    }

    // ------------------------------------------------------------------
    // 작업이력·감사로그
    // ------------------------------------------------------------------

    /** 발행은 워커가 나중에 하므로 행위자를 알 수 있는 시점은 요청 시점뿐이다. */
    @Test
    void 발행_요청은_요청한_사람으로_감사로그에_남는다() {
        givenCards(3);

        service.enqueue(CONTENT_ID, "캡션", ACTOR_ID);

        verify(auditLogService).record(
                eq(ACTOR_ID), eq(AuditAction.PUBLISH), eq(CONTENT_ID), contains("발행 요청"));
    }

    @Test
    void 예약_등록도_감사로그를_남긴다() {
        givenCards(3);

        service.schedule(CONTENT_ID, "캡션", KoreaTime.now().plusMinutes(10), ACTOR_ID);

        verify(auditLogService).record(
                eq(ACTOR_ID), eq(AuditAction.PUBLISH), eq(CONTENT_ID), contains("발행 요청"));
    }

    @Test
    void 발행에_성공하면_작업이력에_permalink가_남는다() {
        givenCards(3);

        PublishRecord record = publish("캡션");

        assertThat(jobHistories).hasSize(1);
        JobHistory history = jobHistories.get(0);
        assertThat(history.getJobType()).isEqualTo(JobType.INSTAGRAM_PUBLISH);
        assertThat(history.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(history.getResultUrl()).isEqualTo(record.getPermalink());
    }

    /**
     * 실패까지 남기면 ContentService.resolveGenerationStatus가 최신 작업 이력의 상태를
     * 콘텐츠 생성 상태로 내려보내 화면에 "생성 실패"로 뜬다. 실패 원인은 publish_records에 남는다.
     */
    @Test
    void 발행에_실패하면_작업이력을_남기지_않는다() {
        givenCards(3);
        instagramClient.setRemainingQuota(0);

        publish("캡션");

        assertThat(jobHistories).isEmpty();
    }
}
