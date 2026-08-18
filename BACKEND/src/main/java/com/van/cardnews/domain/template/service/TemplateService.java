package com.van.cardnews.domain.template.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.van.cardnews.domain.template.dto.request.TemplateCreateRequest;
import com.van.cardnews.domain.template.dto.request.TemplateUpdateRequest;
import com.van.cardnews.domain.template.dto.response.TemplateListResponse;
import com.van.cardnews.domain.template.dto.response.TemplateResponse;
import com.van.cardnews.domain.template.entity.Template;
import com.van.cardnews.domain.template.entity.TemplateContentType;
import com.van.cardnews.domain.template.repository.TemplateRepository;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    /**
     * 활성 템플릿 조회
     *
     * contentType이 없으면 전체 활성 템플릿,
     * contentType이 있으면 해당 유형의 활성 템플릿을 반환합니다.
     */
    public TemplateListResponse getTemplates(
            String contentType
    ) {
        List<Template> templates;

        if (contentType == null || contentType.isBlank()) {
            templates =
                    templateRepository
                            .findByActiveTrueOrderByCodeAscVersionDesc();
        } else {
            TemplateContentType type =
                    parseContentType(contentType);

            templates =
                    templateRepository
                            .findByContentTypeAndActiveTrueOrderByCodeAscVersionDesc(
                                    type
                            );
        }

        Long recommendedTemplateId =
                templates.stream()
                        .findFirst()
                        .map(Template::getId)
                        .orElse(null);

        return new TemplateListResponse(
                templates.stream()
                        .map(TemplateResponse::from)
                        .toList(),
                recommendedTemplateId
        );
    }

    /**
     * 특정 템플릿 버전 조회
     */
    public TemplateResponse getTemplate(Long templateId) {
        Template template =
                templateRepository
                        .findById(templateId)
                        .orElseThrow(() ->
                                new CustomException(
                                        ErrorCode.TEMPLATE_NOT_FOUND
                                )
                        );

        return TemplateResponse.from(template);
    }

    /**
     * 신규 템플릿 등록
     *
     * 새 code는 항상 version 1로 시작합니다.
     */
    @Transactional
    public TemplateResponse createTemplate(
            TemplateCreateRequest request
    ) {
        if (templateRepository
                .findTopByCodeOrderByVersionDesc(request.code())
                .isPresent()) {

            throw new CustomException(
                    ErrorCode.TEMPLATE_CODE_ALREADY_EXISTS,
                    "이미 존재하는 템플릿 코드입니다: " +
                            request.code()
            );
        }

        TemplateContentType contentType =
                parseContentType(request.contentType());

        JsonNode layoutDefinition =
                objectMapper.valueToTree(
                        request.layoutDefinition()
                );

        JsonNode designTokens =
                objectMapper.valueToTree(
                        request.designTokens()
                );

        Template template =
                Template.create(
                        request.code(),
                        request.name(),
                        contentType,
                        request.canvasWidth(),
                        request.canvasHeight(),
                        layoutDefinition,
                        designTokens
                );

        templateRepository.save(template);

        return TemplateResponse.from(template);
    }

    /**
     * 기존 템플릿의 새 버전 생성
     *
     * 기존 버전을 수정하지 않습니다.
     */
    @Transactional
    public TemplateResponse updateTemplate(
            Long templateId,
            TemplateUpdateRequest request
    ) {
        Template current =
                templateRepository
                        .findById(templateId)
                        .orElseThrow(() ->
                                new CustomException(
                                        ErrorCode.TEMPLATE_NOT_FOUND
                                )
                        );

        if (!current.isActive()) {
            throw new CustomException(
                    ErrorCode.TEMPLATE_ALREADY_INACTIVE
            );
        }

        TemplateContentType contentType =
                parseContentType(request.contentType());

        JsonNode layoutDefinition =
                objectMapper.valueToTree(
                        request.layoutDefinition()
                );

        JsonNode designTokens =
                objectMapper.valueToTree(
                        request.designTokens()
                );

        Template newVersion =
                Template.createNewVersion(
                        current,
                        request.name(),
                        contentType,
                        request.canvasWidth(),
                        request.canvasHeight(),
                        layoutDefinition,
                        designTokens
                );

        current.deactivate();

        templateRepository.save(current);
        templateRepository.save(newVersion);

        return TemplateResponse.from(newVersion);
    }

    /**
     * 템플릿 비활성화
     */
    @Transactional
    public void deactivateTemplate(Long templateId) {
        Template template =
                templateRepository
                        .findById(templateId)
                        .orElseThrow(() ->
                                new CustomException(
                                        ErrorCode.TEMPLATE_NOT_FOUND
                                )
                        );

        if (!template.isActive()) {
            throw new CustomException(
                    ErrorCode.TEMPLATE_ALREADY_INACTIVE
            );
        }

        template.deactivate();
    }

    private TemplateContentType parseContentType(
            String value
    ) {
        try {
            return TemplateContentType.from(value);
        } catch (IllegalArgumentException e) {
            throw new CustomException(
                    ErrorCode.INVALID_TEMPLATE_CONTENT_TYPE,
                    e.getMessage()
            );
        }
    }
}
