package com.van.cardnews.domain.content.repository;

import com.van.cardnews.domain.content.entity.Content;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<Content, Long> {
}
