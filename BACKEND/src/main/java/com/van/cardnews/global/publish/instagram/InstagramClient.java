package com.van.cardnews.global.publish.instagram;

import com.van.cardnews.global.instagram.InstagramCredentials;

import java.util.List;

/**
 * 인스타그램 캐러셀 발행 API 호출자입니다. 각 메서드가 Meta API 한 번에 대응합니다.
 *
 * 순서 제어와 폴링은 {@code InstagramPublishService}가 담당합니다.
 * 그래야 dev 프로필에서도 Mock을 상대로 <b>실제 발행 흐름과 폴링 루프가 그대로 실행</b>됩니다.
 * 흐름을 구현 안에 넣으면 그 로직은 prod로 전환하는 순간 처음 실행되는 코드가 됩니다.
 */
public interface InstagramClient {

    /** 캐러셀 최대 장수. Meta 제약입니다. */
    int MAX_CAROUSEL_ITEMS = 10;

    /** 1단계 — 카드 1장을 자식 컨테이너로 만듭니다. */
    String createCarouselItem(InstagramCredentials credentials, String imageUrl, String altText);

    /** 2단계 — 자식들을 묶어 캐러셀 컨테이너를 만듭니다. */
    String createCarouselContainer(InstagramCredentials credentials, List<String> childIds, String caption);

    /** 3단계 — 컨테이너 처리 상태를 조회합니다. */
    ContainerStatus getContainerStatus(InstagramCredentials credentials, String containerId);

    /** 4단계 — 컨테이너를 발행하고 media id를 받습니다. */
    String publishContainer(InstagramCredentials credentials, String containerId);

    /** 5단계 — 발행된 미디어의 permalink를 조회합니다. */
    String getPermalink(InstagramCredentials credentials, String igMediaId);

    /**
     * 24시간 이동 윈도우의 잔여 발행 건수입니다.
     *
     * 소진된 상태에서 컨테이너를 만들면 한도만 더 깎고 실패하므로, 발행 직전에 이 값으로 먼저 막습니다.
     * 한도를 알 수 없는 응답은 {@link Integer#MAX_VALUE}로 돌려줍니다 — 모른다는 이유로 발행을 막지는 않습니다.
     */
    int remainingQuota(InstagramCredentials credentials);

    /**
     * 컨테이너 처리 상태입니다. Meta의 {@code status_code} 값과 같습니다.
     */
    enum ContainerStatus {
        IN_PROGRESS,
        FINISHED,
        PUBLISHED,
        /** 생성 후 24시간이 지나 만료됐습니다. 재사용할 수 없고 처음부터 다시 만들어야 합니다. */
        EXPIRED,
        ERROR
    }
}
