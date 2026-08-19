package com.van.cardnews.domain.template.repository;

import com.van.cardnews.domain.template.entity.Template;
import com.van.cardnews.domain.template.entity.TemplateContentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 특정 접두문자로 시작하는 template code 목록 조회 (중복 제거)
     *
     * 같은 code가 여러 버전(row)으로 존재할 수 있으므로 distinct 처리합니다.
     * 다음 일련번호를 계산하기 위한 용도입니다.
     */
    @Query("""
            select distinct t.code
            from Template t
            where t.code like concat(:prefix, '%')
            """)
    List<String> findDistinctCodeByCodeStartingWith(
            @Param("prefix") String prefix
    );
}
