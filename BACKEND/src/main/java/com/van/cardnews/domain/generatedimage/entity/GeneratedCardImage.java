package com.van.cardnews.domain.generatedimage.entity;

import com.van.cardnews.domain.content.entity.Content;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_card_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeneratedCardImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    /** 몇 번째 카드 세트인지 (예: 0=좌측, 1=중앙, 2=우측) */
    @Column(name = "column_index", nullable = false)
    private int columnIndex;

    /** 세트 내에서의 순번 (CONTENT의 경우 여러 장일 수 있으므로 순서 지정) */
    @Column(name = "card_index", nullable = false)
    private int cardIndex;

    /** 카드뉴스 전체에서의 노출 순서 (정렬용) */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "resolution_width")
    private Integer resolutionWidth;

    @Column(name = "resolution_height")
    private Integer resolutionHeight;

    @Column(name = "storage_ref", length = 500)
    private String storageRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private GeneratedCardImage(
            Content content,
            CardType cardType,
            int columnIndex,
            int cardIndex,
            int sortOrder,
            String imageUrl,
            Integer resolutionWidth,
            Integer resolutionHeight,
            String storageRef
    ) {
        this.content = content;
        this.cardType = cardType;
        this.columnIndex = columnIndex;
        this.cardIndex = cardIndex;
        this.sortOrder = sortOrder;
        this.imageUrl = imageUrl;
        this.resolutionWidth = resolutionWidth;
        this.resolutionHeight = resolutionHeight;
        this.storageRef = storageRef;
    }

    public static GeneratedCardImage create(
            Content content,
            CardType cardType,
            int columnIndex,
            int cardIndex,
            int sortOrder,
            String imageUrl,
            Integer resolutionWidth,
            Integer resolutionHeight,
            String storageRef
    ) {
        return GeneratedCardImage.builder()
                .content(content)
                .cardType(cardType)
                .columnIndex(columnIndex)
                .cardIndex(cardIndex)
                .sortOrder(sortOrder)
                .imageUrl(imageUrl)
                .resolutionWidth(resolutionWidth)
                .resolutionHeight(resolutionHeight)
                .storageRef(storageRef)
                .build();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum CardType { COVER, CONTENT, CLOSING }
}
