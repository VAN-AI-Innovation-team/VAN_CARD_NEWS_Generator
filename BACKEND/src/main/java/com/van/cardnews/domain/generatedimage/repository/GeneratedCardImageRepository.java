package com.van.cardnews.domain.generatedimage.repository;

import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneratedCardImageRepository extends JpaRepository<GeneratedCardImage, Long> {

    List<GeneratedCardImage> findByContent_IdOrderBySortOrderAsc(Long contentId);

    void deleteByContent_Id(Long contentId);
}
