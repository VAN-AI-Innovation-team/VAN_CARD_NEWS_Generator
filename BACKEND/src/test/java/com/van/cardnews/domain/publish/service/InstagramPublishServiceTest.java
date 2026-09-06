package com.van.cardnews.domain.publish.service;

import com.van.cardnews.domain.approval.entity.ApprovalRequest;
import com.van.cardnews.domain.approval.entity.ApprovalStatus;
import com.van.cardnews.domain.approval.repository.ApprovalRequestRepository;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.repository.ContentRepository;
import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import com.van.cardnews.domain.generatedimage.repository.GeneratedCardImageRepository;
import com.van.cardnews.domain.instagram.service.InstagramTokenService;
import com.van.cardnews.domain.publish.entity.PublishRecord;
import com.van.cardnews.domain.publish.entity.PublishStatus;
import com.van.cardnews.domain.publish.repository.PublishRecordRepository;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import com.van.cardnews.global.instagram.InstagramCredentials;
import com.van.cardnews.global.publish.instagram.MockInstagramClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    private static final long RECORD_ID = 7L;
    private static final int MAX_POLL_COUNT = 3;

    private Content content;
    private List<GeneratedCardImage> cards;
    private List<PublishRecord> savedRecords;
    private ApprovalStatus approvalStatus;
    private MockInstagramClient instagramClient;
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

        InstagramTokenService tokenService = mock(InstagramTokenService.class);
        lenient().when(tokenService.current())
                .thenReturn(new InstagramCredentials("dev-ig-user", "dev-dummy-ig-long-lived-token"));

        service = new InstagramPublishService(
                contentRepository,
                approvalRequestRepository,
                cardImageRepository,
                publishRecordRepository,
                tokenService,
                instagramClient);

        ReflectionTestUtils.setField(service, "pollIntervalMs", 1L);
        ReflectionTestUtils.setField(service, "maxPollCount", MAX_POLL_COUNT);
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
        service.enqueue(CONTENT_ID, caption);
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

        PublishRecord record = service.enqueue(CONTENT_ID, "캡션");

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

        assertThatThrownBy(() -> service.enqueue(CONTENT_ID, "캡션"))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.CONTENT_NOT_APPROVED_FOR_PUBLISH.getDefaultMessage())
                // 다운로드용 문구를 재사용하면 "다운로드할 수 있습니다"가 발행 화면에 뜬다
                .hasMessageContaining("발행");
    }

    @Test
    void 카드_이미지가_없으면_큐에_넣지_않는다() {
        assertThatThrownBy(() -> service.enqueue(CONTENT_ID, "캡션"))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.CARD_IMAGES_NOT_READY.getDefaultMessage());
    }

    @Test
    void 이미_발행에_성공했으면_409로_거절한다() {
        givenCards(1);
        publish("캡션");

        assertThatThrownBy(() -> service.enqueue(CONTENT_ID, "캡션"))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.CONTENT_ALREADY_PUBLISHED.getDefaultMessage());
    }

    @Test
    void 진행_중인_건이_있으면_409로_거절한다() {
        givenCards(1);
        service.enqueue(CONTENT_ID, "캡션");

        assertThatThrownBy(() -> service.enqueue(CONTENT_ID, "캡션"))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.PUBLISH_ALREADY_REQUESTED.getDefaultMessage());
    }

    @Test
    void 실패한_건_위에는_다시_요청할_수_있다() {
        givenCards(1);
        instagramClient.failAt(MockInstagramClient.Step.PUBLISH);
        assertThat(publish("캡션").getStatus()).isEqualTo(PublishStatus.FAILED);

        instagramClient.reset();

        assertThat(service.enqueue(CONTENT_ID, "캡션").getStatus()).isEqualTo(PublishStatus.SCHEDULED);
    }

    @Test
    void 없는_콘텐츠는_거절한다() {
        assertThatThrownBy(() -> service.enqueue(999L, "캡션"))
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
                "createCarouselItem:https://example.com/card-0.jpg",
                "createCarouselItem:https://example.com/card-1.jpg",
                "createCarouselContainer:2|caption",
                "getContainerStatus:mock-carousel-3",
                "getContainerStatus:mock-carousel-3",
                "publishContainer:mock-carousel-3",
                "getPermalink:mock-media-4");
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

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getErrorMessage()).contains("폴링");
        assertThat(callCount("getContainerStatus")).isEqualTo(MAX_POLL_COUNT);
        assertThat(callCount("publishContainer")).isZero();
    }

    @Test
    void 컨테이너가_만료되면_즉시_실패로_기록한다() {
        givenCards(1);
        instagramClient.failAt(MockInstagramClient.Step.STATUS_EXPIRED);

        PublishRecord record = publish("캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getErrorMessage()).contains("EXPIRED");
        assertThat(callCount("getContainerStatus")).isEqualTo(1);
    }

    @Test
    void 컨테이너가_ERROR면_실패로_기록한다() {
        givenCards(1);
        instagramClient.failAt(MockInstagramClient.Step.STATUS_ERROR);

        assertThat(publish("캡션").getErrorMessage()).contains("ERROR");
    }

    /**
     * 발행 실패를 예외로 올리면 트랜잭션이 롤백되어 실패 기록 자체가 사라진다.
     * 재시도(VAN-13)가 기댈 근거가 이 행이므로 예외 대신 FAILED로 남긴다.
     */
    @Test
    void 발행_단계_실패는_예외_대신_FAILED로_기록한다() {
        givenCards(2);
        instagramClient.failAt(MockInstagramClient.Step.PUBLISH);

        PublishRecord record = publish("캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getErrorMessage()).isNotBlank();
        assertThat(record.getIgMediaId()).isNull();
    }

    @Test
    void 카드가_11장이면_호출_전에_걸러낸다() {
        givenCards(11);

        PublishRecord record = publish("캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getErrorMessage()).contains("최대 10장");
        assertThat(instagramClient.calls()).isEmpty();
    }

    @Test
    void 없는_발행_건은_실행할_수_없다() {
        assertThatThrownBy(() -> service.execute(RECORD_ID))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.PUBLISH_RECORD_NOT_FOUND.getDefaultMessage());
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
}
