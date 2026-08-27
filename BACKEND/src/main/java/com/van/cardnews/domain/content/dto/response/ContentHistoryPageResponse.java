package com.van.cardnews.domain.content.dto.response;

import java.util.List;

/**
 * 콘텐츠 이력 목록의 페이징 응답입니다.
 */
public record ContentHistoryPageResponse(
        List<ContentManagementListResponse> contents,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
