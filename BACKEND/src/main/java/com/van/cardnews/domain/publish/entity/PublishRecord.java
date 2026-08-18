package com.van.cardnews.domain.publish.entity;

import com.van.cardnews.domain.content.entity.Content;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 콘텐츠 게시 실행 이력을 관리합니다.
 *
 * PDF의 PublishRecord에 대응합니다.
 *
 * 하나의 Content는 채널별로 여러 게시 이력을 가질 수 있습니다.
 * 게시 실패 시 retryCount를 통해 재시도 횟수를 추적합니다.
 */
@Entity
@Table(name = "publish_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublishRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * PDF의 draft_id에 대응합니다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    /**
     * 게시 채널.
     *
     * 예:
     * INSTAGRAM
     * HOMEPAGE
     * LINKEDIN
     *
     * 실제 사용 가능한 채널은 추후 확정합니다.
     */
    @Column(nullable = false, length = 50)
    private String channel;

    /**
     * 외부 채널에서 발급한 게시물 ID.
     */
    @Column(name = "external_post_id", length = 200)
    private String externalPostId;

    /**
     * 게시 완료 시각.
     */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublishResult result;

    /**
     * 게시 실패 원인.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 재시도 횟수.
     *
     * 최초 실행은 0,
     * 실패 후 재시도할 때마다 1씩 증가합니다.
     */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Builder
    private PublishRecord(
            Content content,
            String channel
    ) {
        this.content = content;
        this.channel = channel;
        this.result = PublishResult.PENDING;
        this.retryCount = 0;
    }

    public static PublishRecord create(
            Content content,
            String channel
    ) {
        return PublishRecord.builder()
                .content(content)
                .channel(channel)
                .build();
    }

    /**
     * 게시 성공 처리.
     */
    public void markSuccess(String externalPostId) {
        this.result = PublishResult.SUCCESS;
        this.externalPostId = externalPostId;
        this.publishedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    /**
     * 게시 실패 처리.
     */
    public void markFailed(String errorMessage) {
        this.result = PublishResult.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * 게시 재시도.
     */
    public void retry() {
        if (this.result != PublishResult.FAILED) {
            throw new IllegalStateException(
                    "실패한 게시 건만 재시도할 수 있습니다."
            );
        }

        this.retryCount++;
        this.result = PublishResult.PENDING;
        this.errorMessage = null;
        this.publishedAt = null;
    }
}
