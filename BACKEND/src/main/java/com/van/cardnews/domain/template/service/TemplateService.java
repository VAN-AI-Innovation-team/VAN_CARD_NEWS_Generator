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
import org.springframework.dao.DataIntegrityViolationException;
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
     * code는 contentType에 따라 서버가 자동으로 확정합니다.
     * 형식: {접두문자}{일련번호} (예: A1, A2, B1 ...)
     * 새 code는 항상 version 1로 시작합니다.
     *
     * 동시성 참고: 매우 드문 경우지만 같은 contentType에 대한
     * 등록 요청이 정확히 동시에 들어오면 동일한 code가 계산되어
     * DB 유니크 제약(uk_templates_code_version) 위반이 발생할 수
     * 있습니다. 이 경우 명확한 오류를 반환하므로 클라이언트가
     * 재요청하면 됩니다. 관리자가 수동으로 저빈도로 수행하는
     * 작업이므로 이 정도 처리로 충분합니다.
     */
    @Transactional
    public TemplateResponse createTemplate(
            TemplateCreateRequest request
    ) {
        TemplateContentType contentType =
                parseContentType(request.contentType());

        String code = generateNextCode(contentType);

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
                        code,
                        request.name(),
                        contentType,
                        request.canvasWidth(),
                        request.canvasHeight(),
                        layoutDefinition,
                        designTokens
                );

        try {
            // saveAndFlush로 즉시 INSERT를 실행해 유니크 제약 위반을
            // 이 메서드 안에서 바로 감지합니다.
            templateRepository.saveAndFlush(template);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(
                    ErrorCode.TEMPLATE_CODE_ALREADY_EXISTS,
                    "템플릿 코드 생성 중 충돌이 발생했습니다. 다시 시도해주세요: " + code
            );
        }

        return TemplateResponse.from(template);
    }

    /**
     * 콘텐츠 유형에 해당하는 다음 템플릿 code를 계산합니다.
     *
     * 형식: {접두문자}{일련번호} (예: A1, A2 ...)
     * 접두문자 뒤에 붙은 숫자 중 최댓값 + 1을 다음 번호로 사용합니다.
     */
    private String generateNextCode(TemplateContentType contentType) {
        String prefix = contentType.getCodePrefix();

        List<String> existingCodes =
                templateRepository.findDistinctCodeByCodeStartingWith(prefix);

        int nextSequence = existingCodes.stream()
                .map(c -> c.substring(prefix.length()))
                .filter(seq -> seq.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0)
                + 1;

        return prefix + nextSequence;
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
