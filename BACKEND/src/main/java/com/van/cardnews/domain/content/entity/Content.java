package com.van.cardnews.domain.content.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.van.cardnews.domain.template.entity.Template;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용자가 입력한 카드뉴스 생성 요청 원본 데이터.
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentStatus status;

    @OneToMany(
            mappedBy = "content",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ContentImage> images = new ArrayList<>();

    // 💡 1. 카드 생성 결과 필드 (CardGenerationService 등에서 사용)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_generation_result", columnDefinition = "json")
    private JsonNode cardGenerationResult;

    // 💡 2. 카드 이미지 배치 정보 필드 (ImagePlacementResolver, ContentPreviewResponse 등에서 사용)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_image_placements", columnDefinition = "json")
    private JsonNode cardImagePlacements;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Content(
            String title,
            String body,
            Template template
    ) {
        this.title = title;
        this.body = body;
        this.template = template;
        this.status = ContentStatus.DRAFT;
    }

    public static Content create(
            String title,
            String body,
            Template template
    ) {
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

    // 1번 필드 업데이트 메서드
    public void updateCardGenerationResult(JsonNode cardGenerationResult) {
        this.cardGenerationResult = cardGenerationResult;
    }

    // 2번 필드 업데이트 메서드
    public void updateCardImagePlacements(JsonNode cardImagePlacements) {
        this.cardImagePlacements = cardImagePlacements;
    }

    public void publish() {
        this.status = ContentStatus.PUBLISHED;
    }

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
