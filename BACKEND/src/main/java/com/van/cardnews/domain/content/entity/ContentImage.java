package com.van.cardnews.domain.content.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Content 하나에 첨부되는 이미지입니다.
 * 관리 정보:
 * - 원본 이미지 경로
 * - 크롭 영역
 * - 생성 이미지 경로
 * - 순번
 * - 생성 이미지 해상도
 * - 스토리지 참조
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

    /**
     * 원본 이미지의 접근 URL 또는 경로.
     */
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    /**
     * 크롭 영역.
     *
     * 현재는 JSON 문자열 형태로 저장할 수 있도록 TEXT로 둡니다.
     * 예:
     * {"x":10,"y":20,"width":500,"height":500}
     */
    @Column(name = "crop_area", columnDefinition = "TEXT")
    private String cropArea;

    /**
     * 카드뉴스 생성 후 생성된 이미지의 접근 URL 또는 경로.
     */
    @Column(name = "generated_image_url", length = 500)
    private String generatedImageUrl;

    /**
     * 콘텐츠 내 이미지 순번.
     */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * 생성 이미지 해상도 - 가로.
     */
    @Column(name = "resolution_width")
    private Integer resolutionWidth;

    /**
     * 생성 이미지 해상도 - 세로.
     */
    @Column(name = "resolution_height")
    private Integer resolutionHeight;

    /**
     * 스토리지 참조값.
     *
     * 현재는 로컬 스토리지 URL을 사용할 수 있으며,
     * 추후 오브젝트 스토리지로 변경할 경우 실제 객체 식별자를 저장할 수 있습니다.
     */
    @Column(name = "storage_ref", length = 500)
    private String storageRef;

    @Builder
    private ContentImage(
            String imageUrl,
            String cropArea,
            String generatedImageUrl,
            int sortOrder,
            Integer resolutionWidth,
            Integer resolutionHeight,
            String storageRef
    ) {
        this.imageUrl = imageUrl;
        this.cropArea = cropArea;
        this.generatedImageUrl = generatedImageUrl;
        this.sortOrder = sortOrder;
        this.resolutionWidth = resolutionWidth;
        this.resolutionHeight = resolutionHeight;
        this.storageRef = storageRef;
    }

    /**
     * 원본 이미지 등록.
     */
    public static ContentImage create(
            String imageUrl,
            int sortOrder
    ) {
        return ContentImage.builder()
                .imageUrl(imageUrl)
                .sortOrder(sortOrder)
                .build();
    }

    /**
     * 크롭 정보와 생성 이미지 결과를 저장합니다.
     */
    public void updateGenerationResult(
            String cropArea,
            String generatedImageUrl,
            Integer resolutionWidth,
            Integer resolutionHeight,
            String storageRef
    ) {
        this.cropArea = cropArea;
        this.generatedImageUrl = generatedImageUrl;
        this.resolutionWidth = resolutionWidth;
        this.resolutionHeight = resolutionHeight;
        this.storageRef = storageRef;
    }

    void assignContent(Content content) {
        this.content = content;
    }
}
