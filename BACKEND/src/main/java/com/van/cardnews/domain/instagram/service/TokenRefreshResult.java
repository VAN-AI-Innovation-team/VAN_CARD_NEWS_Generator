package com.van.cardnews.domain.instagram.service;

public enum TokenRefreshResult {

    /** 갱신을 수행했고 만료일이 새로 밀렸습니다. */
    REFRESHED,

    /** 발급 24시간이 지나지 않아 Meta가 갱신을 거부하므로 호출하지 않았습니다. */
    SKIPPED_TOO_EARLY
}
