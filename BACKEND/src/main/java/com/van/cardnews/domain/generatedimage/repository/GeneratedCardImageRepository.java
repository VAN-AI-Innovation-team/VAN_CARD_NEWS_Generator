package com.van.cardnews.domain.generatedimage.repository;

import com.van.cardnews.domain.generatedimage.entity.GeneratedCardImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GeneratedCardImageRepository extends JpaRepository<GeneratedCardImage, Long> {

    List<GeneratedCardImage> findByContent_IdOrderBySortOrderAsc(Long contentId);

    Optional<GeneratedCardImage> findByIdAndContent_Id(
            Long imageId,
            Long contentId
    );

    long countByContent_Id(Long contentId);

    void deleteByContent_Id(Long contentId);
}
