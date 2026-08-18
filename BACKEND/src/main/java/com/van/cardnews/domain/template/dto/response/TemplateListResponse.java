package com.van.cardnews.domain.template.dto.response;

import java.util.List;

public record TemplateListResponse(
        List<TemplateResponse> templates,
        Long recommendedTemplateId
) {
}
