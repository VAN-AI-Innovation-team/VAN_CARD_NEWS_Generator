package com.van.cardnews.domain.instagram.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 인스타그램 장기 액세스 토큰입니다. 팀 계정 1개 고정이므로 1행만 존재합니다.
 *
 * 토큰은 AES/GCM 암호문으로만 보관하며, 평문은 {@code TokenCipher}를 거쳐야 얻을 수 있습니다.
 * 이 엔티티는 평문을 필드로도 갖지 않으므로 로그·예외에 실려 나갈 경로가 없습니다.
 */
@Entity
@Table(name = "instagram_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstagramToken {

    /** Meta는 발급 24시간이 지나기 전의 갱신 요청을 거부합니다. */
    public static final Duration MIN_AGE_BEFORE_REFRESH = Duration.ofHours(24);

    /** 만료가 이만큼 남으면 경고 로그를 남깁니다. */
    public static final long EXPIRY_WARNING_DAYS = 14;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ig_user_id", nullable = false, length = 50, unique = true)
    private String igUserId;

    @Column(name = "access_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String accessTokenEncrypted;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_refreshed_at")
    private LocalDateTime lastRefreshedAt;

    private InstagramToken(
            String igUserId,
            String accessTokenEncrypted,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        this.igUserId = igUserId;
        this.accessTokenEncrypted = accessTokenEncrypted;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 최초 발급된 장기 토큰을 저장 가능한 형태로 만듭니다.
     */
    public static InstagramToken issue(
            String igUserId,
            String accessTokenEncrypted,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        return new InstagramToken(igUserId, accessTokenEncrypted, issuedAt, expiresAt);
    }

    /**
     * 갱신 응답을 반영합니다. 갱신 시점이 곧 새 발급 시점이라 24시간 규칙의 기준도 함께 밀립니다.
     */
    public void applyRefresh(String accessTokenEncrypted, LocalDateTime refreshedAt, LocalDateTime expiresAt) {
        this.accessTokenEncrypted = accessTokenEncrypted;
        this.issuedAt = refreshedAt;
        this.expiresAt = expiresAt;
        this.lastRefreshedAt = refreshedAt;
    }

    /**
     * 발급 24시간이 지나야 갱신할 수 있습니다.
     */
    public boolean isRefreshableAt(LocalDateTime now) {
        return !now.isBefore(issuedAt.plus(MIN_AGE_BEFORE_REFRESH));
    }

    public long daysUntilExpiry(LocalDateTime now) {
        return Duration.between(now, expiresAt).toDays();
    }

    public boolean isNearingExpiryAt(LocalDateTime now) {
        return daysUntilExpiry(now) <= EXPIRY_WARNING_DAYS;
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public LocalDateTime refreshableFrom() {
        return issuedAt.plus(MIN_AGE_BEFORE_REFRESH);
    }

    /** 식별자와 만료일만 노출합니다. 토큰 값은 어떤 경로로도 문자열에 담기지 않습니다. */
    @Override
    public String toString() {
        return "InstagramToken(igUserId=" + igUserId + ", expiresAt=" + expiresAt + ")";
    }
}
