package com.van.cardnews.domain.template.repository;

import com.van.cardnews.domain.template.entity.Template;
import com.van.cardnews.domain.template.entity.TemplateContentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository
        extends JpaRepository<Template, Long> {

    List<Template> findByContentTypeAndActiveTrueOrderByCodeAscVersionDesc(
            TemplateContentType contentType
    );

    List<Template> findByActiveTrueOrderByCodeAscVersionDesc();

    Optional<Template> findByIdAndActiveTrue(Long id);

    Optional<Template> findTopByCodeOrderByVersionDesc(
            String code
    );
}
