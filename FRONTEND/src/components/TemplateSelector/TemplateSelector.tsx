import { useTemplate } from '../../contexts/TemplateContext';
import './TemplateSelector.css';

export function TemplateSelector() {
  const { templates, selectedTemplateId, setSelectedTemplateId } =
    useTemplate();

  return (
    <section className="tpl-selector" aria-label="카드뉴스 템플릿 선택">
      <div className="tpl-selector__header">
        <span className="tpl-selector__eyebrow">Index · 템플릿 목록</span>
        <h2 className="tpl-selector__title">어떤 형식의 카드뉴스인가요?</h2>
      </div>

      <div
        className="tpl-selector__grid"
        role="radiogroup"
        aria-label="템플릿 종류"
      >
        {templates.map((tpl) => {
          const isSelected = tpl.id === selectedTemplateId;
          return (
            <button
              key={tpl.id}
              type="button"
              role="radio"
              aria-checked={isSelected}
              className={`tpl-card${isSelected ? ' tpl-card--selected' : ''}`}
              onClick={() => setSelectedTemplateId(tpl.id)}
            >
              <span className="tpl-card__corner" aria-hidden="true" />
              <span className="tpl-card__letter">{tpl.id}</span>
              <span className="tpl-card__tag">{tpl.tag}</span>
              <span className="tpl-card__label">{tpl.label}</span>

              <span className="tpl-card__row">
                <span className="tpl-card__row-label">주요 내용</span>
                <span className="tpl-card__row-value">{tpl.mainContent}</span>
              </span>
              <span className="tpl-card__row">
                <span className="tpl-card__row-label">디자인 특징</span>
                <span className="tpl-card__row-value">{tpl.designFeature}</span>
              </span>

              {isSelected && (
                <span className="tpl-card__stamp" aria-hidden="true">
                  SELECTED
                </span>
              )}
            </button>
          );
        })}
      </div>
    </section>
  );
}

export default TemplateSelector;
