import type {
  CardGenerationResult,
  LayoutCard,
  LayoutElement,
  PreviewImage,
  PreviewTemplate,
} from '../../api/contentApi';

import './CardNewsPreview.css';

interface CardNewsPreviewProps {
  template: PreviewTemplate;
  cardGenerationResult: CardGenerationResult;
  images: PreviewImage[];
  cardIndex: number;
}

interface RenderContext {
  template: PreviewTemplate;
  content:
    | CardGenerationResult['cover']
    | CardGenerationResult['content'][number]
    | CardGenerationResult['closing'];
  images: PreviewImage[];
}

function getTokenValue(
  tokens: Record<string, string>,
  token?: string,
): string | undefined {
  if (!token) {
    return undefined;
  }

  return tokens[token];
}

function getText(role: string, content: RenderContext['content']): string {
  if (role === 'title') {
    return 'title' in content ? (content.title ?? '') : '';
  }

  if (role === 'body') {
    return 'body' in content ? (content.body ?? '') : '';
  }

  if (role === 'highlight') {
    return 'highlight' in content ? (content.highlight ?? '') : '';
  }

  if (role === 'cta') {
    return 'cta' in content ? (content.cta ?? '') : '';
  }

  return '';
}

function getLayoutCard(
  template: PreviewTemplate,
  cardIndex: number,
  result: CardGenerationResult,
): {
  card: LayoutCard;
  content: RenderContext['content'];
} {
  if (cardIndex === 0) {
    return {
      card: template.layout.cards.cover,
      content: result.cover,
    };
  }

  if (cardIndex <= result.content.length) {
    return {
      card: template.layout.cards.content,
      content: result.content[cardIndex - 1],
    };
  }

  return {
    card: template.layout.cards.closing,
    content: result.closing,
  };
}

function renderElement(
  elementKey: string,
  element: LayoutElement,
  context: RenderContext,
) {
  const { template, content, images } = context;

  const style = {
    left: `${element.x}%`,
    top: `${element.y}%`,
    width: `${element.width}%`,
    height: `${element.height}%`,
    zIndex: element.layer ?? 0,
    color: getTokenValue(template.designTokens.colors, element.colorToken),
    backgroundColor: getTokenValue(
      template.designTokens.colors,
      element.backgroundToken,
    ),
    textAlign: element.align ?? 'left',
  } as React.CSSProperties;

  if (element.role === 'image') {
    const image = images[0];

    return (
      <div
        key={elementKey}
        className="card-news-preview__element"
        style={style}
      >
        {image && (
          <img
            src={image.imageUrl}
            alt=""
            className="card-news-preview__image"
          />
        )}
      </div>
    );
  }

  if (
    element.role === 'background' ||
    element.role === 'overlay' ||
    element.role === 'panel'
  ) {
    return (
      <div
        key={elementKey}
        className="card-news-preview__element"
        style={style}
      />
    );
  }

  if (
    element.role === 'title' ||
    element.role === 'body' ||
    element.role === 'highlight' ||
    element.role === 'eyebrow'
  ) {
    const text = element.text ?? getText(element.role, content);

    return (
      <div
        key={elementKey}
        className={`card-news-preview__element card-news-preview__text card-news-preview__text--${element.role}`}
        style={style}
      >
        {text}
      </div>
    );
  }

  return null;
}

export default function CardNewsPreview({
  template,
  cardGenerationResult,
  images,
  cardIndex,
}: CardNewsPreviewProps) {
  const { card, content } = getLayoutCard(
    template,
    cardIndex,
    cardGenerationResult,
  );

  return (
    <div
      className="card-news-preview"
      style={{
        aspectRatio: `${template.canvasWidth} / ${template.canvasHeight}`,
      }}
    >
      {Object.entries(card.elements)
        .sort(([, a], [, b]) => (a.layer ?? 0) - (b.layer ?? 0))
        .map(([key, element]) =>
          renderElement(key, element, {
            template,
            content,
            images,
          }),
        )}
    </div>
  );
}
