package com.van.cardnews.global.publish.instagram;

import com.van.cardnews.global.instagram.InstagramCredentials;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * dev 프로필용 발행 목업입니다.
 *
 * 이 에픽은 실계정 전환(VAN-18) 전까지 <b>전 기간을 목업으로만 검증</b>합니다.
 * 그래서 이 클래스는 보조 장치가 아니라 1급 산출물이고, 다음을 실구현과 같게 흉내냅니다.
 *
 * <ul>
 *   <li>자식 컨테이너 → 캐러셀 컨테이너 → 상태 폴링 → 발행 순서와 자격·개수 제약</li>
 *   <li>상태 전이 — 첫 조회는 {@code IN_PROGRESS}, 그 다음부터 {@code FINISHED}.
 *       한 번은 반드시 돌게 해서 폴링 루프 자체가 dev에서 실행되게 한다</li>
 *   <li>호출 기록({@link #calls()}) — 테스트가 호출 횟수·순서를 단언할 수 있다</li>
 *   <li>실패 주입({@link #failAt(Step)}) — 발행 실패 분기(VAN-13)를 검증할 유일한 수단이다</li>
 * </ul>
 */
@Component
@Profile("dev")
public class MockInstagramClient implements InstagramClient {

    /** 실패를 주입할 지점입니다. */
    public enum Step {
        CREATE_ITEM,
        CREATE_CONTAINER,
        /** 컨테이너가 ERROR 상태로 떨어집니다. */
        STATUS_ERROR,
        /** 컨테이너가 24시간 만료로 떨어집니다. */
        STATUS_EXPIRED,
        /** 폴링이 끝나지 않습니다(타임아웃 경로). */
        STATUS_NEVER_FINISH,
        PUBLISH
    }

    private final AtomicLong sequence = new AtomicLong();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> statusChecks = new ConcurrentHashMap<>();
    private volatile Step failAt;

    @Override
    public String createCarouselItem(InstagramCredentials credentials, String imageUrl, String altText) {
        requireCredentials(credentials);
        calls.add("createCarouselItem:" + imageUrl + (altText == null ? "" : "|alt"));

        if (failAt == Step.CREATE_ITEM) {
            throw new IllegalStateException("자식 컨테이너 생성 실패 (mock)");
        }

        return "mock-child-" + sequence.incrementAndGet();
    }

    @Override
    public String createCarouselContainer(InstagramCredentials credentials, List<String> childIds, String caption) {
        requireCredentials(credentials);
        calls.add("createCarouselContainer:" + childIds.size()
                + (caption == null || caption.isBlank() ? "" : "|caption"));

        // 실구현이 Meta에게 거절당할 입력은 목업도 거절해야 한다. 그래야 순서·개수 회귀를 dev에서 잡는다.
        if (childIds.isEmpty() || childIds.size() > MAX_CAROUSEL_ITEMS) {
            throw new IllegalArgumentException("캐러셀은 1~" + MAX_CAROUSEL_ITEMS + "장이어야 합니다.");
        }
        if (failAt == Step.CREATE_CONTAINER) {
            throw new IllegalStateException("캐러셀 컨테이너 생성 실패 (mock)");
        }

        return "mock-carousel-" + sequence.incrementAndGet();
    }

    @Override
    public ContainerStatus getContainerStatus(InstagramCredentials credentials, String containerId) {
        requireCredentials(credentials);
        calls.add("getContainerStatus:" + containerId);

        if (failAt == Step.STATUS_ERROR) {
            return ContainerStatus.ERROR;
        }
        if (failAt == Step.STATUS_EXPIRED) {
            return ContainerStatus.EXPIRED;
        }
        if (failAt == Step.STATUS_NEVER_FINISH) {
            return ContainerStatus.IN_PROGRESS;
        }

        int checks = statusChecks.computeIfAbsent(containerId, key -> new AtomicInteger())
                .incrementAndGet();

        return checks == 1 ? ContainerStatus.IN_PROGRESS : ContainerStatus.FINISHED;
    }

    @Override
    public String publishContainer(InstagramCredentials credentials, String containerId) {
        requireCredentials(credentials);
        calls.add("publishContainer:" + containerId);

        if (failAt == Step.PUBLISH) {
            throw new IllegalStateException("발행 실패 (mock)");
        }

        return "mock-media-" + sequence.incrementAndGet();
    }

    @Override
    public String getPermalink(InstagramCredentials credentials, String igMediaId) {
        requireCredentials(credentials);
        calls.add("getPermalink:" + igMediaId);

        return "https://www.instagram.com/p/" + igMediaId + "/";
    }

    /** 이번 세션의 호출 기록입니다. 순서대로 쌓입니다. */
    public List<String> calls() {
        return new ArrayList<>(calls);
    }

    public void failAt(Step step) {
        this.failAt = step;
    }

    public void reset() {
        this.failAt = null;
        this.calls.clear();
        this.statusChecks.clear();
    }

    /**
     * 목업이라도 자격 없이 통과시키지 않습니다.
     * dev에서 토큰 조회 경로가 끊겨도 초록불이 뜨면, 그 배선은 prod 전환 때 처음 실행됩니다.
     */
    private void requireCredentials(InstagramCredentials credentials) {
        if (credentials == null
                || credentials.igUserId() == null || credentials.igUserId().isBlank()
                || credentials.accessToken() == null || credentials.accessToken().isBlank()) {
            throw new IllegalArgumentException("인스타그램 자격(ig_user_id/access_token)이 필요합니다.");
        }
    }
}
