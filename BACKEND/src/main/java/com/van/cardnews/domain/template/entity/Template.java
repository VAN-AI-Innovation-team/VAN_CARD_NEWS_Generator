package com.van.cardnews.domain.template.entity;

import com.van.cardnews.global.time.KoreaTime;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "templates",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_templates_code_version",
                        columnNames = {"code", "version"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 내부 템플릿 코드.
     *
     * 예:
     * A, B, C, D, E ...
     *
     * 사용자 화면에는 노출하지 않습니다.
     */
    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "content_type", nullable = false, length = 30)
    private TemplateContentType contentType;

    @Column(name = "canvas_width", nullable = false)
    private int canvasWidth;

    @Column(name = "canvas_height", nullable = false)
    private int canvasHeight;

    /**
     * 카드뉴스 레이아웃 정의
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "layout_definition",
            nullable = false,
            columnDefinition = "jsonb"
    )
    private JsonNode layoutDefinition;

    /**
     * 색상/서체 디자인 토큰
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "design_tokens",
            nullable = false,
            columnDefinition = "jsonb"
    )
    private JsonNode designTokens;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Template(
            String code,
            String name,
            TemplateContentType contentType,
            int canvasWidth,
            int canvasHeight,
            JsonNode layoutDefinition,
            JsonNode designTokens,
            boolean active,
            int version
    ) {
        this.code = code;
        this.name = name;
        this.contentType = contentType;
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.layoutDefinition = layoutDefinition;
        this.designTokens = designTokens;
        this.active = active;
        this.version = version;
    }

    /**
     * 신규 템플릿 등록
     */
    public static Template create(
            String code,
            String name,
            TemplateContentType contentType,
            int canvasWidth,
            int canvasHeight,
            JsonNode layoutDefinition,
            JsonNode designTokens
    ) {
        return new Template(
                code,
                name,
                contentType,
                canvasWidth,
                canvasHeight,
                layoutDefinition,
                designTokens,
                true,
                1
        );
    }

    /**
     * 기존 템플릿의 새 버전 생성
     *
     * 기존 Entity는 변경하지 않고 새로운 row를 생성합니다.
     */
    public static Template createNewVersion(
            Template previousVersion,
            String name,
            TemplateContentType contentType,
            int canvasWidth,
            int canvasHeight,
            JsonNode layoutDefinition,
            JsonNode designTokens
    ) {
        return new Template(
                previousVersion.code,
                name,
                contentType,
                canvasWidth,
                canvasHeight,
                layoutDefinition,
                designTokens,
                true,
                previousVersion.version + 1
        );
    }

    public void deactivate() {
        this.active = false;
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
