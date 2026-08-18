import type { ContentType } from '../../types/content';
import './ContentTypeSelector.css';

interface ContentTypeOption {
  id: ContentType;
  number: string;
  label: string;
  description: string;
}

interface ContentTypeSelectorProps {
  selectedType: ContentType | null;
  onSelect: (type: ContentType) => void;
}

const CONTENT_TYPES: ContentTypeOption[] = [
  {
    id: 'recruitment',
    number: '01',
    label: '모집',
    description: '회원·참가자·지원자 모집 안내',
  },
  {
    id: 'event',
    number: '02',
    label: '행사',
    description: '컨퍼런스·세미나·행사 안내',
  },
  {
    id: 'news',
    number: '03',
    label: '소식',
    description: '학회 활동·성과·주요 소식',
  },
  {
    id: 'quote',
    number: '04',
    label: '인용',
    description: '주요 발언·인터뷰·인용문',
  },
];

function ContentTypeSelector({
  selectedType,
  onSelect,
}: ContentTypeSelectorProps) {
  return (
    <section className="content-type-selector" aria-label="콘텐츠 유형 선택">
      <div
        className="content-type-selector__grid"
        role="radiogroup"
        aria-label="콘텐츠 유형"
      >
        {CONTENT_TYPES.map((type) => {
          const isSelected = selectedType === type.id;

          return (
            <button
              key={type.id}
              type="button"
              role="radio"
              aria-checked={isSelected}
              className={`content-type-card ${
                isSelected ? 'content-type-card--selected' : ''
              }`}
              onClick={() => onSelect(type.id)}
            >
              <span className="content-type-card__top">
                <span className="content-type-card__number">{type.number}</span>

                <span
                  className={`content-type-card__radio ${
                    isSelected ? 'content-type-card__radio--selected' : ''
                  }`}
                  aria-hidden="true"
                >
                  {isSelected && (
                    <span className="content-type-card__radio-dot" />
                  )}
                </span>
              </span>

              <span className="content-type-card__label">{type.label}</span>

              <span className="content-type-card__description">
                {type.description}
              </span>

              <span className="content-type-card__select">
                {isSelected ? '선택됨' : '선택'}
              </span>
            </button>
          );
        })}
      </div>

      <p className="content-type-selector__hint">
        선택한 콘텐츠 유형을 기준으로 적합한 카드뉴스 템플릿을 추천합니다.
      </p>
    </section>
  );
}

export default ContentTypeSelector;
