import axios from 'axios';

import type { ContentType } from '../types/content';
import type { Template, TemplateListResponse } from '../types/template';

const TEMPLATE_API_URL = '/api/templates';

/**
 * 활성 템플릿 목록 조회
 *
 * contentType이 전달되면 해당 콘텐츠 유형에 사용할 수 있는
 * 템플릿을 조회합니다.
 *
 * 응답에는 템플릿 목록과 추천 템플릿 ID가 함께 포함됩니다.
 */
export async function fetchTemplates(params?: {
  contentType?: ContentType;
  active?: boolean;
}): Promise<TemplateListResponse> {
  const response = await axios.get<TemplateListResponse>(TEMPLATE_API_URL, {
    params: {
      contentType: params?.contentType,
      active: params?.active ?? true,
    },
  });

  return response.data;
}

/**
 * 특정 템플릿 조회
 */
export async function fetchTemplate(templateId: number): Promise<Template> {
  const response = await axios.get<Template>(
    `${TEMPLATE_API_URL}/${templateId}`,
  );

  return response.data;
}
