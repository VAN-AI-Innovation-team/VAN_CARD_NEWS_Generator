import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

import { fetchTemplates } from '../api/templateApi';

import type { ContentType } from '../types/content';
import type { Template } from '../types/template';

interface TemplateContextValue {
  /**
   * 현재 콘텐츠 유형에 대해 조회된 템플릿
   */
  templates: Template[];

  /**
   * 현재 추천 템플릿 ID
   *
   * API가 추천하지 않는 경우 null
   */
  recommendedTemplateId: number | null;

  /**
   * 현재 선택된 템플릿 ID
   */
  selectedTemplateId: number | null;

  /**
   * 현재 선택된 템플릿
   */
  selectedTemplate: Template | null;

  /**
   * 마지막 선택 변경 시각
   */
  lastChangedAt: number | null;

  /**
   * 템플릿 API 조회 중
   */
  isLoading: boolean;

  /**
   * 템플릿 API 오류
   */
  error: string | null;

  /**
   * 콘텐츠 유형 기준으로 템플릿 조회
   */
  reloadTemplates: (contentType: ContentType) => Promise<void>;

  /**
   * 템플릿 선택
   */
  setSelectedTemplateId: (templateId: number) => void;

  /**
   * 선택된 템플릿 초기화
   */
  clearSelectedTemplate: () => void;
}

const TemplateContext = createContext<TemplateContextValue | undefined>(
  undefined,
);

interface TemplateProviderProps {
  children: ReactNode;
}

export function TemplateProvider({ children }: TemplateProviderProps) {
  const [templates, setTemplatesState] = useState<Template[]>([]);

  const [recommendedTemplateId, setRecommendedTemplateId] = useState<
    number | null
  >(null);

  const [selectedTemplateId, setSelectedTemplateIdState] = useState<
    number | null
  >(null);

  const [lastChangedAt, setLastChangedAt] = useState<number | null>(null);

  const [isLoading, setIsLoading] = useState(false);

  const [error, setError] = useState<string | null>(null);

  /**
   * 콘텐츠 유형에 맞는 활성 템플릿 조회
   */
  const reloadTemplates = useCallback(async (contentType: ContentType) => {
    try {
      setIsLoading(true);
      setError(null);

      const response = await fetchTemplates({
        contentType,
        active: true,
      });

      setTemplatesState(response.templates);

      setRecommendedTemplateId(response.recommendedTemplateId);

      /**
       * 이전에 선택한 템플릿이
       * 새로 조회한 목록에 없다면 초기화합니다.
       */
      setSelectedTemplateIdState((currentSelectedId) => {
        if (currentSelectedId === null) {
          return null;
        }

        const exists = response.templates.some(
          (template) => template.id === currentSelectedId,
        );

        return exists ? currentSelectedId : null;
      });
    } catch (requestError) {
      console.error('템플릿 목록 조회 실패:', requestError);

      setTemplatesState([]);
      setRecommendedTemplateId(null);
      setSelectedTemplateIdState(null);

      setError('템플릿 정보를 불러오지 못했습니다.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  /**
   * 템플릿 선택
   */
  const setSelectedTemplateId = useCallback((templateId: number) => {
    setSelectedTemplateIdState(templateId);
    setLastChangedAt(Date.now());
  }, []);

  /**
   * 선택 초기화
   */
  const clearSelectedTemplate = useCallback(() => {
    setSelectedTemplateIdState(null);
    setLastChangedAt(Date.now());
  }, []);

  /**
   * 선택된 템플릿 계산
   */
  const selectedTemplate = useMemo<Template | null>(
    () =>
      templates.find((template) => template.id === selectedTemplateId) ?? null,
    [templates, selectedTemplateId],
  );

  const value = useMemo<TemplateContextValue>(
    () => ({
      templates,

      recommendedTemplateId,

      selectedTemplateId,

      selectedTemplate,

      lastChangedAt,

      isLoading,

      error,

      reloadTemplates,

      setSelectedTemplateId,

      clearSelectedTemplate,
    }),
    [
      templates,
      recommendedTemplateId,
      selectedTemplateId,
      selectedTemplate,
      lastChangedAt,
      isLoading,
      error,
      reloadTemplates,
      setSelectedTemplateId,
      clearSelectedTemplate,
    ],
  );

  return (
    <TemplateContext.Provider value={value}>
      {children}
    </TemplateContext.Provider>
  );
}

/**
 * TemplateContext 사용 Hook
 */
export function useTemplate(): TemplateContextValue {
  const context = useContext(TemplateContext);

  if (context === undefined) {
    throw new Error(
      'useTemplate()은 반드시 <TemplateProvider> 내부에서 호출해야 합니다.',
    );
  }

  return context;
}
