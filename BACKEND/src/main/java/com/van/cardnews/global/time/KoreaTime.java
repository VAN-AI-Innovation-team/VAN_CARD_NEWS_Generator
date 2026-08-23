package com.van.cardnews.global.time;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 애플리케이션에서 사용하는 기준 시간입니다.
 *
 * DB의 TIMESTAMPTZ와 연동되는 생성/처리 시각을
 * 대한민국 표준시(Asia/Seoul) 기준의 LocalDateTime으로 관리합니다.
 */
public final class KoreaTime {

    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

    private KoreaTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE_ID);
    }
}
