package com.van.cardnews.domain.content.entity;

import com.van.cardnews.global.time.KoreaTime;

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

    /**
     * 미리보기에서 사용자가 고친 발행 캡션. {@code null}이면 아직 고친 적이 없다는 뜻이고,
     * 그때는 {@code PublishTextComposer}가 조립한 값이 쓰인다.
     */
    @Column(name = "publish_caption", columnDefinition = "TEXT")
    private String publishCaption;

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

    public void updateBasicInfo(
            String title,
            String body,
            Template template
    ) {
        this.title = title;
        this.body = body;
        this.template = template;
        this.cardGenerationResult = null;
        this.cardImagePlacements = null;
        // 캡션도 같이 버린다. 본문이 바뀐 뒤 옛 문구가 남아 있으면 그게 그대로 발행된다.
        this.publishCaption = null;
    }

    public void removeImagesNotIn(java.util.Set<Long> keepImageIds) {
        this.images.removeIf(image -> !keepImageIds.contains(image.getId()));
        reorderImages();
    }

    public void reorderImages() {
        for (int i = 0; i < images.size(); i++) {
            images.get(i).updateSortOrder(i);
        }
    }

    // 1번 필드 업데이트 메서드
    public void updateCardGenerationResult(JsonNode cardGenerationResult) {
        this.cardGenerationResult = cardGenerationResult;
    }

    public void updateTemplate(Template template) {
        this.template = template;
    }

    // 2번 필드 업데이트 메서드
    public void updateCardImagePlacements(JsonNode cardImagePlacements) {
        this.cardImagePlacements = cardImagePlacements;
    }

    /** 캡션은 발행 전용 값이라 카드 구성 결과를 건드리지 않는다. */
    public void updatePublishCaption(String publishCaption) {
        this.publishCaption = publishCaption;
    }

    public void publish() {
        this.status = ContentStatus.PUBLISHED;
    }

    public void archive() {
        this.status = ContentStatus.ARCHIVED;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = KoreaTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = KoreaTime.now();
    }
}
