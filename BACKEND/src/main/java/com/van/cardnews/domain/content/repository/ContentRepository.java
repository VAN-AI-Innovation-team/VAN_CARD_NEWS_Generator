package com.van.cardnews.domain.content.repository;

import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.entity.ContentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContentRepository extends JpaRepository<Content, Long> {

    /**
     * 생성 파이프라인용 Content 조회입니다.
     *
     * Content의 images와 template을 함께 조회하여
     * 비동기 파이프라인에서 LazyInitializationException이
     * 발생하지 않도록 합니다.
     */
    @Query("""
            select distinct c
            from Content c
            left join fetch c.images
            left join fetch c.template
            where c.id = :contentId
            """)
    Optional<Content> findByIdWithImages(
            @Param("contentId") Long contentId
    );

    List<Content> findByStatusNotOrderByCreatedAtDesc(ContentStatus status);
}
