package com.van.cardnews.domain.content.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용자가 입력한 카드뉴스 생성 요청 원본 데이터.
 * contents 테이블 및 content_images 테이블과 매핑되는 엔티티입니다.
 *   - status: 발행 상태 (DRAFT, PUBLISHED, ARCHIVED)
 *   - images: content_images 테이블과 1:N 양방향 연관관계
 */
@Entity
@Table(name = "contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** 카드뉴스 템플릿 종류 (예: academic_index_card 등) */
    @Column(length = 100)
    private String template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentStatus status;

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContentImage> images = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Content(String title, String body, String template) {
        this.title = title;
        this.body = body;
        this.template = template;
        this.status = ContentStatus.DRAFT;
    }

    public static Content create(String title, String body, String template) {
        return Content.builder()
                .title(title)
                .body(body)
                .template(template)
                .build();
    }

    public void addImage(ContentImage image) {
        this.images.add(image);
        image.assignContent(this);
    }

    public void publish() {
        this.status = ContentStatus.PUBLISHED;
    }

    /** 하드 삭제 대신 사용하는 소프트 삭제 (job_histories FK가 RESTRICT 제약조건임) */
    public void archive() {
        this.status = ContentStatus.ARCHIVED;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
