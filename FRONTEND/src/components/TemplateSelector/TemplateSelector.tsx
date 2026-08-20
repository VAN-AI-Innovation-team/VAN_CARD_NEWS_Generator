import { useEffect, useMemo } from 'react';

import { useTemplate } from '../../contexts/TemplateContext';

import type { ContentType } from '../../types/content';

import './TemplateSelector.css';

interface TemplateSelectorProps {
  contentType: ContentType | null;
}

const CONTENT_TYPE_LABELS: Record<ContentType, string> = {
  recruitment: '모집',
  event: '행사',
  news: '소식',
  quote: '인용',
};

export function TemplateSelector({ contentType }: TemplateSelectorProps) {
  const {
    templates,
    selectedTemplateId,
    setSelectedTemplateId,
    reloadTemplates,
    isLoading,
    error,
  } = useTemplate();

  /**
   * 컴포넌트 마운트 시 전체 활성 템플릿을 조회합니다.
   */
  useEffect(() => {
    void reloadTemplates();
  }, [reloadTemplates]);

  /**
   * 정렬 로직:
   * 현재 선택된 콘텐츠 유형(contentType)과 일치하는 템플릿들을 1순위(최상단)로 정렬하고,
   * 그 외의 템플릿들을 2순위로 아래에 배치합니다.
   */
  const sortedTemplates = useMemo(() => {
    return [...templates].sort((a, b) => {
      const aMatchesType = contentType ? a.contentType === contentType : false;
      const bMatchesType = contentType ? b.contentType === contentType : false;

      // 선택한 콘텐츠 유형과 일치하는 템플릿을 최상단으로 정렬 (-1)
      if (aMatchesType && !bMatchesType) {
        return -1;
      }
      if (!aMatchesType && bMatchesType) {
        return 1;
      }

      // 둘 다 일치하거나 둘 다 아니면 기존 순서 유지
      return 0;
    });
  }, [templates, contentType]);

  return (
    <section className="tpl-selector" aria-label="카드뉴스 템플릿 선택">
      {contentType && (
        <div className="tpl-selector__type">
          <span className="tpl-selector__type-label">선택한 콘텐츠 유형</span>

          <strong>{CONTENT_TYPE_LABELS[contentType]}</strong>
        </div>
      )}

      {isLoading ? (
        <div className="tpl-selector__empty">
          <p className="tpl-selector__empty-title">
            템플릿을 불러오는 중입니다.
          </p>

          <p className="tpl-selector__empty-description">
            잠시만 기다려주세요.
          </p>
        </div>
      ) : error ? (
        <div className="tpl-selector__empty">
          <p className="tpl-selector__empty-title">
            템플릿 정보를 불러오지 못했습니다.
          </p>

          <p className="tpl-selector__empty-description">
            잠시 후 다시 시도해주세요.
          </p>
        </div>
      ) : sortedTemplates.length > 0 ? (
        <div
          className="tpl-selector__grid"
          role="radiogroup"
          aria-label="템플릿 종류"
        >
          {sortedTemplates.map((template) => {
            const isSelected = template.id === selectedTemplateId;

            // 선택한 콘텐츠 유형과 일치하는 모든 템플릿을 '추천'으로 처리
            const isRecommended = contentType
              ? template.contentType === contentType
              : false;

            const templateContentTypeLabel =
              CONTENT_TYPE_LABELS[template.contentType as ContentType] ??
              '소식';

            return (
              <button
                key={template.id}
                type="button"
                role="radio"
                aria-checked={isSelected}
                className={`tpl-card ${
                  isRecommended ? 'tpl-card--recommended' : ''
                } ${isSelected ? 'tpl-card--selected' : ''}`}
                onClick={() => setSelectedTemplateId(template.id)}
              >
                <span className="tpl-card__top">
                  <span className="tpl-card__tag">
                    {templateContentTypeLabel}
                  </span>

                  {isRecommended && (
                    <span className="tpl-card__recommend">추천</span>
                  )}
                </span>

                <span className="tpl-card__label">{template.name}</span>

                <span className="tpl-card__row">
                  <span className="tpl-card__row-label">캔버스</span>

                  <span className="tpl-card__row-value">
                    {template.canvas.width} × {template.canvas.height}
                  </span>
                </span>

                <span className="tpl-card__row">
                  <span className="tpl-card__row-label">레이아웃</span>

                  <span className="tpl-card__row-value">
                    {template.layout.description}
                  </span>
                </span>

                {isSelected && (
                  <span className="tpl-card__selected" aria-hidden="true">
                    선택됨
                  </span>
                )}
              </button>
            );
          })}
        </div>
      ) : (
        <div className="tpl-selector__empty">
          <p className="tpl-selector__empty-title">
            현재 사용할 수 있는 템플릿이 없습니다.
          </p>

          <p className="tpl-selector__empty-description">
            등록된 활성 템플릿이 없습니다.
          </p>
        </div>
      )}
    </section>
  );
}

export default TemplateSelector;
