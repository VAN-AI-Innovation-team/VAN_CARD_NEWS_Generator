package com.van.cardnews.domain.instagram.repository;

import com.van.cardnews.domain.instagram.entity.InstagramToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstagramTokenRepository extends JpaRepository<InstagramToken, Long> {

    /**
     * 팀 계정 1개 고정이라 저장된 토큰은 1건뿐입니다.
     */
    Optional<InstagramToken> findFirstByOrderByIdAsc();
}
