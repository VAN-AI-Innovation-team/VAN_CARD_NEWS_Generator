package com.van.cardnews.domain.download.entity;

import com.van.cardnews.global.time.KoreaTime;
import com.van.cardnews.domain.content.entity.Content;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 콘텐츠 다운로드 시도 이력.
 *
 * 하나의 다운로드 요청은 하나의 이력으로 남기며,
 * 실패 후 다시 요청한 경우 retryCount가 증가합니다.
 */
@Entity
@Table(name = "download_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DownloadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Column(nullable = false, length = 50)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "download_type", nullable = false, length = 20)
    private DownloadType downloadType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DownloadResult result;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "image_id")
    private Long imageId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    private DownloadHistory(
            Content content,
            String channel,
            DownloadType downloadType,
            int retryCount,
            Long imageId
    ) {
        this.content = content;
        this.channel = channel;
        this.downloadType = downloadType;
        this.retryCount = retryCount;
        this.imageId = imageId;
        this.result = DownloadResult.PENDING;
        this.requestedAt = KoreaTime.now();
    }

    public static DownloadHistory create(
            Content content,
            String channel,
            DownloadType downloadType,
            int retryCount,
            Long imageId
    ) {
        return DownloadHistory.builder()
                .content(content)
                .channel(channel)
                .downloadType(downloadType)
                .retryCount(retryCount)
                .imageId(imageId)
                .build();
    }

    public void markSuccess() {
        this.result = DownloadResult.SUCCESS;
        this.errorMessage = null;
        this.completedAt = KoreaTime.now();
    }

    public void markFailed(String errorMessage) {
        this.result = DownloadResult.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = KoreaTime.now();
    }
}
