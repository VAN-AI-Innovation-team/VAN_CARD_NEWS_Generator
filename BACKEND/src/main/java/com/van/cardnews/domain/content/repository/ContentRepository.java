package com.van.cardnews.domain.content.repository;

import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.entity.ContentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    /**
     * 콘텐츠 목록 조회
     */
    List<Content> findByStatusNotOrderByCreatedAtDesc(ContentStatus status);

    /**
     * 콘텐츠 이력 페이징 조회입니다.
     * ARCHIVED 콘텐츠는 기존 목록 정책과 동일하게 제외합니다.
     */
    Page<Content> findByStatusNot(ContentStatus status, Pageable pageable);

    /**
     * 승인 요청 생성용 비관적 쓰기 잠금입니다.
     *
     * 동일 콘텐츠에 동시에 승인 요청이 들어오는 경우
     * 한 트랜잭션이 Content row를 먼저 잠그고,
     * 트랜잭션이 종료될 때까지 다른 승인 요청이 대기하도록 합니다.
     *
     * Service의 PENDING 중복 검사와 함께 사용하여
     * race condition으로 인한 중복 승인 요청 생성을 방지합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from Content c
            where c.id = :contentId
            """)
    Optional<Content> findByIdForApproval(
            @Param("contentId") Long contentId
    );
}
