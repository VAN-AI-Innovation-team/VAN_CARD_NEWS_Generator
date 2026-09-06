package com.van.cardnews.domain.publish.entity;

import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.global.time.KoreaTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 콘텐츠의 외부 채널 발행 이력이자 예약 큐입니다.
 *
 * 인스타그램은 예약 발행 API를 제공하지 않으므로 예약의 단일 저장소가 이 테이블이며,
 * 즉시 발행도 scheduledAt을 현재 시각으로 두고 같은 경로를 탑니다.
 *
 * 같은 콘텐츠/채널의 SUCCESS는 부분 유니크 인덱스로 1건만 허용됩니다.
 */
@Entity
@Table(name = "publish_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublishRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Column(nullable = false, length = 50)
    private String channel;

    /**
     * media_publish 응답으로 받은 인스타그램 미디어 ID입니다.
     */
    @Column(name = "ig_media_id", length = 100)
    private String igMediaId;

    @Column(columnDefinition = "TEXT")
    private String permalink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublishStatus status;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /**
     * 워커가 이 건을 선점한 시각입니다.
     * PROCESSING 상태로 멈춘 건을 회수할 때 판정 기준이 됩니다.
     */
    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    /**
     * 예약 시점의 캡션을 고정 보관합니다.
     * 예약 후 발행 전에 콘텐츠가 수정되어도 의도한 문구가 나가도록 합니다.
     */
    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Builder
    private PublishRecord(
            Content content,
            String channel,
            String caption,
            LocalDateTime scheduledAt
    ) {
        this.content = content;
        this.channel = channel;
        this.caption = caption;
        this.scheduledAt = scheduledAt;
        this.status = PublishStatus.SCHEDULED;
        this.retryCount = 0;
        this.requestedAt = KoreaTime.now();
    }

    /**
     * 예약 발행 건을 생성합니다.
     */
    public static PublishRecord schedule(
            Content content,
            String channel,
            String caption,
            LocalDateTime scheduledAt
    ) {
        return PublishRecord.builder()
                .content(content)
                .channel(channel)
                .caption(caption)
                .scheduledAt(scheduledAt)
                .build();
    }

    /**
     * 즉시 발행 건을 생성합니다. 예약 큐를 그대로 타되 scheduledAt이 현재 시각입니다.
     */
    public static PublishRecord publishNow(
            Content content,
            String channel,
            String caption
    ) {
        PublishRecord record = schedule(content, channel, caption, KoreaTime.now());
        record.status = PublishStatus.PENDING;
        return record;
    }

    /**
     * 워커가 이 건을 선점합니다.
     */
    public void markProcessing() {
        this.status = PublishStatus.PROCESSING;
        this.processingStartedAt = KoreaTime.now();
    }

    public void markSuccess(String igMediaId, String permalink) {
        this.status = PublishStatus.SUCCESS;
        this.igMediaId = igMediaId;
        this.permalink = permalink;
        this.publishedAt = KoreaTime.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = PublishStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * 실패한 건을 다시 큐에 넣습니다.
     */
    public void retry() {
        if (this.status != PublishStatus.FAILED) {
            throw new IllegalStateException("실패한 발행 건만 재시도할 수 있습니다.");
        }

        this.retryCount++;
        this.status = PublishStatus.PENDING;
        this.errorMessage = null;
        this.processingStartedAt = null;
    }

    /**
     * 발행 전 건을 취소합니다.
     */
    public void cancel() {
        if (this.status == PublishStatus.SUCCESS) {
            throw new IllegalStateException("이미 발행된 건은 취소할 수 없습니다.");
        }

        this.status = PublishStatus.CANCELED;
    }
}
