package com.van.cardnews.domain.content.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Content 하나에 첨부되는 이미지(최대 10장). content_images 테이블에 매핑됩니다.
 * FK는 ON DELETE CASCADE이므로 Content 삭제(하드 삭제) 시 함께 정리됩니다.
 * (다만 실제 서비스에서는 하드 삭제 대신 Content.status = ARCHIVED로 소프트 삭제하는 것을 권장합니다.)
 */
@Entity
@Table(name = "content_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private ContentImage(String imageUrl, int sortOrder) {
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    public static ContentImage create(String imageUrl, int sortOrder) {
        return ContentImage.builder()
                .imageUrl(imageUrl)
                .sortOrder(sortOrder)
                .build();
    }

    void assignContent(Content content) {
        this.content = content;
    }
}
