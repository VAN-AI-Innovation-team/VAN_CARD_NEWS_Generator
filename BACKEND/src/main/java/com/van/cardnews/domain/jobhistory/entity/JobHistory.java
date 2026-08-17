package com.van.cardnews.domain.jobhistory.entity;

import com.van.cardnews.domain.content.entity.Content;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * job_histories 테이블과 매핑됩니다.
 * content_id FK는 ON DELETE RESTRICT이므로, 이력이 남아있는 content는 삭제할 수 없습니다.
 */
@Entity
@Table(name = "job_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    /** 작업 유형 (COPY_GENERATION / IMAGE_GENERATION / FULL_PIPELINE) */
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobStatus status;

    @Column(name = "result_url", columnDefinition = "TEXT")
    private String resultUrl;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    private JobHistory(Content content, JobType jobType) {
        this.content = content;
        this.jobType = jobType;
        this.status = JobStatus.PENDING;
        this.requestedAt = LocalDateTime.now();
    }

    public static JobHistory createPending(Content content, JobType jobType) {
        return JobHistory.builder()
                .content(content)
                .jobType(jobType)
                .build();
    }

    public void markProcessing() {
        this.status = JobStatus.PROCESSING;
    }

    public void markCompleted(String resultUrl) {
        this.status = JobStatus.COMPLETED;
        this.resultUrl = resultUrl;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }
}
