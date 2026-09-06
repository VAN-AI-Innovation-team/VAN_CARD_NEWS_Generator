package com.van.cardnews.domain.publish.service;

import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.repository.ContentRepository;
import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import com.van.cardnews.domain.generatedimage.repository.GeneratedCardImageRepository;
import com.van.cardnews.domain.instagram.service.InstagramTokenService;
import com.van.cardnews.domain.publish.entity.PublishRecord;
import com.van.cardnews.domain.publish.entity.PublishStatus;
import com.van.cardnews.domain.publish.repository.PublishRecordRepository;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.instagram.InstagramCredentials;
import com.van.cardnews.global.publish.instagram.MockInstagramClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * dev 프로필 기준 — MockInstagramClient로 발행 전 플로우를 검증한다.
 *
 * 이 에픽은 실계정 전환(VAN-18) 전까지 목업으로만 검증되므로, Mock을 상대로
 * <b>순서·호출 횟수·상태 전이·실패 분기</b>까지 단언한다. "예외가 안 났다"만 보면
 * 폴링 루프처럼 실행 여부가 드러나지 않는 코드가 미검증으로 남는다.
 */
class InstagramPublishServiceTest {

    private static final long CONTENT_ID = 42L;
    private static final int MAX_POLL_COUNT = 3;

    private Content content;
    private List<GeneratedCardImage> cards;
    private MockInstagramClient instagramClient;
    private InstagramPublishService service;

    @BeforeEach
    void setUp() {
        content = mock(Content.class);
        cards = new ArrayList<>();
        instagramClient = new MockInstagramClient();

        ContentRepository contentRepository = mock(ContentRepository.class);
        lenient().when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(content));

        GeneratedCardImageRepository cardImageRepository = mock(GeneratedCardImageRepository.class);
        lenient().when(cardImageRepository.findByContent_IdOrderBySortOrderAsc(anyLong()))
                .thenAnswer(invocation -> cards);

        PublishRecordRepository publishRecordRepository = mock(PublishRecordRepository.class);
        when(publishRecordRepository.save(org.mockito.ArgumentMatchers.<PublishRecord>any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InstagramTokenService tokenService = mock(InstagramTokenService.class);
        lenient().when(tokenService.current())
                .thenReturn(new InstagramCredentials("dev-ig-user", "dev-dummy-ig-long-lived-token"));

        service = new InstagramPublishService(
                contentRepository, cardImageRepository, publishRecordRepository, tokenService, instagramClient);

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

    private long callCount(String prefix) {
        return instagramClient.calls().stream().filter(call -> call.startsWith(prefix)).count();
    }

    @Test
    void 카드_3장을_캐러셀로_발행하고_기록한다() {
        givenCards(3);

        PublishRecord record = service.publishNow(CONTENT_ID, "테스트 캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.SUCCESS);
        assertThat(record.getChannel()).isEqualTo(InstagramPublishService.CHANNEL);
        assertThat(record.getIgMediaId()).startsWith("mock-media-");
        assertThat(record.getPermalink()).contains(record.getIgMediaId());
        assertThat(record.getCaption()).isEqualTo("테스트 캡션");
        assertThat(record.getPublishedAt()).isNotNull();
        assertThat(record.getErrorMessage()).isNull();
    }

    @Test
    void 자식_컨테이너부터_permalink까지_정해진_순서로_호출한다() {
        givenCards(2);

        service.publishNow(CONTENT_ID, "캡션");

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

        service.publishNow(CONTENT_ID, "캡션");

        assertThat(callCount("getContainerStatus")).isEqualTo(2);
    }

    @Test
    void 폴링이_끝나지_않으면_최대_횟수에서_실패로_기록한다() {
        givenCards(1);
        instagramClient.failAt(MockInstagramClient.Step.STATUS_NEVER_FINISH);

        PublishRecord record = service.publishNow(CONTENT_ID, "캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getErrorMessage()).contains("폴링");
        assertThat(callCount("getContainerStatus")).isEqualTo(MAX_POLL_COUNT);
        assertThat(callCount("publishContainer")).isZero();
    }

    @Test
    void 컨테이너가_만료되면_즉시_실패로_기록한다() {
        givenCards(1);
        instagramClient.failAt(MockInstagramClient.Step.STATUS_EXPIRED);

        PublishRecord record = service.publishNow(CONTENT_ID, "캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getErrorMessage()).contains("EXPIRED");
        assertThat(callCount("getContainerStatus")).isEqualTo(1); // 재시도하지 않는다
    }

    @Test
    void 컨테이너가_ERROR면_실패로_기록한다() {
        givenCards(1);
        instagramClient.failAt(MockInstagramClient.Step.STATUS_ERROR);

        PublishRecord record = service.publishNow(CONTENT_ID, "캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getErrorMessage()).contains("ERROR");
    }

    /**
     * 발행 실패를 예외로 올리면 트랜잭션이 롤백되어 실패 기록 자체가 사라진다.
     * 재시도(VAN-13)가 기댈 근거가 이 행이므로 예외 대신 FAILED로 남긴다.
     */
    @Test
    void 발행_단계_실패는_예외_대신_FAILED로_기록한다() {
        givenCards(2);
        instagramClient.failAt(MockInstagramClient.Step.PUBLISH);

        PublishRecord record = service.publishNow(CONTENT_ID, "캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getErrorMessage()).isNotBlank();
        assertThat(record.getIgMediaId()).isNull();
    }

    @Test
    void 카드가_없으면_Meta를_호출하지_않는다() {
        PublishRecord record = service.publishNow(CONTENT_ID, "캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getErrorMessage()).contains("카드 이미지가 없습니다");
        assertThat(instagramClient.calls()).isEmpty();
    }

    /** 캐러셀 상한 초과는 호출 전에 걸러야 한다 — 자식 컨테이너를 만들고 나서 알면 그만큼 낭비다. */
    @Test
    void 카드가_11장이면_호출_전에_걸러낸다() {
        givenCards(11);

        PublishRecord record = service.publishNow(CONTENT_ID, "캡션");

        assertThat(record.getStatus()).isEqualTo(PublishStatus.FAILED);
        assertThat(record.getErrorMessage()).contains("최대 10장");
        assertThat(instagramClient.calls()).isEmpty();
    }

    @Test
    void 없는_콘텐츠는_발행_기록을_남기지_않고_거절한다() {
        assertThatThrownBy(() -> service.publishNow(999L, "캡션"))
                .isInstanceOf(CustomException.class);
    }
}
