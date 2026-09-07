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
   * 현재 조회된 전체 템플릿
   */
  templates: Template[];

  /**
   * 현재 추천 템플릿 ID (단일 값)
   */
  recommendedTemplateId: number | null;

  /**
   * 정렬 및 복수 추천 처리를 위한 추천 템플릿 ID 목록 (배열)
   */
  recommendedTemplateIds: number[];

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
   * 템플릿 조회 (contentType은 선택 사항으로 변경하여 전체 조회 지원)
   */
  reloadTemplates: (contentType?: ContentType | string) => Promise<void>;

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

  const recommendedTemplateIds = useMemo(() => {
    return recommendedTemplateId != null ? [recommendedTemplateId] : [];
  }, [recommendedTemplateId]);

  /**
   * 전체 활성 템플릿 조회 (contentType이 안 들어와도 전체를 조회하도록 처리)
   */
  const reloadTemplates = useCallback(
    async (contentType?: ContentType | string) => {
      try {
        setIsLoading(true);
        setError(null);

        // 인자로 전달된 값이 있다면 사용하고, 없다면 undefined 전달하여 전체 조회
        const response = await fetchTemplates({
          contentType: contentType as ContentType,
          active: true,
        });

        setTemplatesState(response.templates);
        setRecommendedTemplateId(response.recommendedTemplateId);

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
    },
    [],
  );

  const setSelectedTemplateId = useCallback((templateId: number) => {
    setSelectedTemplateIdState(templateId);
    setLastChangedAt(Date.now());
  }, []);

  const clearSelectedTemplate = useCallback(() => {
    setSelectedTemplateIdState(null);
    setLastChangedAt(Date.now());
  }, []);

  const selectedTemplate = useMemo<Template | null>(
    () =>
      templates.find((template) => template.id === selectedTemplateId) ?? null,
    [templates, selectedTemplateId],
  );

  const value = useMemo<TemplateContextValue>(
    () => ({
      templates,
      recommendedTemplateId,
      recommendedTemplateIds,
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
      recommendedTemplateIds,
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

export function useTemplate(): TemplateContextValue {
  const context = useContext(TemplateContext);

  if (context === undefined) {
    throw new Error(
      'useTemplate()은 반드시 <TemplateProvider> 내부에서 호출해야 합니다.',
    );
  }

  return context;
}

export default TemplateContext;
