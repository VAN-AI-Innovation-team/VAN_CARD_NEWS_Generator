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
    recommendedTemplateId,
    selectedTemplateId,
    setSelectedTemplateId,
    reloadTemplates,
    isLoading,
    error,
  } = useTemplate();

  /**
   * 콘텐츠 유형이 변경되면
   * 해당 유형의 활성 템플릿을 다시 조회합니다.
   */
  useEffect(() => {
    if (!contentType) {
      return;
    }

    void reloadTemplates(contentType);
  }, [contentType, reloadTemplates]);

  /**
   * API에서 받은 추천 템플릿을 맨 앞으로 배치합니다.
   *
   * 어떤 코드가 추천되는지는 프론트에서 판단하지 않습니다.
   */
  const sortedTemplates = useMemo(() => {
    if (recommendedTemplateId === null) {
      return templates;
    }

    return [...templates].sort((a, b) => {
      if (a.id === recommendedTemplateId) {
        return -1;
      }

      if (b.id === recommendedTemplateId) {
        return 1;
      }

      return 0;
    });
  }, [templates, recommendedTemplateId]);

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

            const isRecommended = template.id === recommendedTemplateId;

            const contentTypeLabel = CONTENT_TYPE_LABELS[contentType ?? 'news'];

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
                  <span className="tpl-card__tag">{contentTypeLabel}</span>

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
            선택한 콘텐츠 유형에 등록된 활성 템플릿이 없습니다.
          </p>
        </div>
      )}
    </section>
  );
}

export default TemplateSelector;
