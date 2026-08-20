import CardNewsPreview from './CardNewsPreview';

import type {
  CardGenerationResult,
  PreviewImage,
  PreviewTemplate,
} from '../../api/contentApi';

import './CardNewsPreviewSet.css';

interface CardNewsPreviewSetProps {
  template: PreviewTemplate;
  cardGenerationResult: CardGenerationResult;
  images: PreviewImage[];
}

export default function CardNewsPreviewSet({
  template,
  cardGenerationResult,
  images,
}: CardNewsPreviewSetProps) {
  const cardCount = cardGenerationResult.content.length + 2;

  return (
    <div className="card-news-preview-set">
      {Array.from({ length: cardCount }, (_, index) => (
        <article key={index} className="card-news-preview-set__item">
          <div className="card-news-preview-set__number">
            CARD {String(index + 1).padStart(2, '0')}
          </div>

          <CardNewsPreview
            template={template}
            cardGenerationResult={cardGenerationResult}
            images={images}
            cardIndex={index}
          />
        </article>
      ))}
    </div>
  );
}
