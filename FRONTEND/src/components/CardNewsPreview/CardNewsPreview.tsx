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

/**
 * 채우기(면) 역할과 텍스트 역할을 구분합니다.
 *
 * - SHAPE_ROLES: 배경/오버레이/패널/구분선처럼 "면"을 그리는 요소.
 *   colorToken 값을 배경색(backgroundColor)으로 사용합니다.
 * - TEXT_ROLES: 실제 글자를 그리는 요소.
 *   colorToken은 글자색(color), backgroundToken은 뱃지/칩의 배경색으로 사용합니다.
 */
const SHAPE_ROLES = new Set(['background', 'overlay', 'panel', 'divider']);
const TEXT_ROLES = new Set([
  'title',
  'body',
  'highlight',
  'eyebrow',
  'footer',
  'badge',
  'decoration',
]);

function getTokenValue(
  tokens: Record<string, string>,
  token?: string,
): string | undefined {
  if (!token) {
    return undefined;
  }

  return tokens[token];
}

function getContentFieldValue(
  field: LayoutElement['contentField'],
  content: RenderContext['content'],
): string {
  if (!field) {
    return '';
  }

  if (field === 'title') {
    return 'title' in content ? (content.title ?? '') : '';
  }

  if (field === 'body') {
    return 'body' in content ? (content.body ?? '') : '';
  }

  if (field === 'highlight') {
    return 'highlight' in content ? (content.highlight ?? '') : '';
  }

  if (field === 'date') {
    return 'date' in content ? (content.date ?? '') : '';
  }

  if (field === 'location') {
    return 'location' in content ? (content.location ?? '') : '';
  }

  if (field === 'cta') {
    return 'cta' in content ? (content.cta ?? '') : '';
  }

  return '';
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

function getContentImage(
  content: RenderContext['content'],
  images: PreviewImage[],
): PreviewImage | undefined {
  if (!('imageId' in content) || content.imageId == null) {
    return undefined;
  }

  return images.find((image) => image.id === content.imageId);
}

/**
 * designTokens.typography는 `${typographyToken}Size` / `${typographyToken}Weight` /
 * `${typographyToken}LineHeight` / `${typographyToken}LetterSpacing` 형태의 키를
 * 가진 값 모음입니다. Size/LetterSpacing은 1080px 캔버스 기준 px 값으로 저장되어
 * 있으므로, 실제 렌더링 시에는 캔버스 폭에 대한 비율(cqw)로 환산해 적용합니다.
 * cqw는 `.card-news-preview`에 선언된 컨테이너 크기를 기준으로 계산되므로,
 * 카드가 화면에서 어떤 크기로 표시되든 항상 동일한 비율로 스케일됩니다.
 */
function getTypographyStyle(
  typography: Record<string, string>,
  typographyToken: string | undefined,
  canvasWidth: number,
): React.CSSProperties {
  if (!typographyToken || !canvasWidth) {
    return {};
  }

  const toCqw = (raw?: string): string | undefined => {
    if (!raw) {
      return undefined;
    }

    const numeric = Number.parseFloat(raw);

    if (Number.isNaN(numeric)) {
      return undefined;
    }

    return `${(numeric / canvasWidth) * 100}cqw`;
  };

  const style: React.CSSProperties = {};

  const fontSize = toCqw(typography[`${typographyToken}Size`]);
  if (fontSize) {
    style.fontSize = fontSize;
  }

  const fontWeight = typography[`${typographyToken}Weight`];
  if (fontWeight) {
    style.fontWeight = fontWeight as React.CSSProperties['fontWeight'];
  }

  const lineHeight = typography[`${typographyToken}LineHeight`];
  if (lineHeight) {
    style.lineHeight = lineHeight;
  }

  const letterSpacing = toCqw(typography[`${typographyToken}LetterSpacing`]);
  if (letterSpacing) {
    style.letterSpacing = letterSpacing;
  }

  return style;
}

/** shape 속성에 따른 모서리 처리. pill은 완전한 알약형, badge/chip은 둥근 사각형입니다. */
function getShapeStyle(element: LayoutElement): React.CSSProperties {
  if (element.shape === 'pill') {
    return { borderRadius: '999px' };
  }

  if (element.shape === 'badge' || element.shape === 'chip') {
    return { borderRadius: '1.4cqw' };
  }

  return {};
}

function getImageCropStyle(
  content: RenderContext['content'],
): React.CSSProperties {
  if (!('cropArea' in content) || !content.cropArea) {
    return {};
  }

  const { x, y, width, height } = content.cropArea;

  if (width >= 100 && height >= 100 && x === 0 && y === 0) {
    return {};
  }

  const zoom = Math.max(1, 100 / Math.min(width, height));
  const centerX = x + width / 2;
  const centerY = y + height / 2;

  return {
    objectPosition: `${centerX}% ${centerY}%`,
    transform: `scale(${zoom})`,
    transformOrigin: `${centerX}% ${centerY}%`,
  };
}

function renderElement(
  elementKey: string,
  element: LayoutElement,
  context: RenderContext,
) {
  const { template, content, images } = context;
  const { colors, typography } = template.designTokens;

  const style: React.CSSProperties = {
    left: `${element.x}%`,
    top: `${element.y}%`,
    width: `${element.width}%`,
    height: `${element.height}%`,
    zIndex: element.layer ?? 0,
    textAlign: element.align ?? 'left',
  };

  if (element.role === 'image') {
    const image = getContentImage(content, images);

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
            style={getImageCropStyle(content)}
          />
        )}
      </div>
    );
  }

  if (SHAPE_ROLES.has(element.role)) {
    // 면 요소는 colorToken을 "채우는 색"으로 해석합니다.
    style.backgroundColor = getTokenValue(colors, element.colorToken);
    Object.assign(style, getShapeStyle(element));

    return (
      <div
        key={elementKey}
        className={`card-news-preview__element card-news-preview__shape card-news-preview__shape--${element.role}`}
        style={style}
      />
    );
  }

  if (TEXT_ROLES.has(element.role)) {
    // 텍스트 요소는 colorToken이 글자색, backgroundToken이 칩/뱃지 배경색입니다.
    style.color = getTokenValue(colors, element.colorToken);

    const backgroundColor = getTokenValue(colors, element.backgroundToken);
    if (backgroundColor) {
      style.backgroundColor = backgroundColor;
    }

    Object.assign(
      style,
      getTypographyStyle(
        typography,
        element.typographyToken,
        template.canvasWidth,
      ),
    );
    Object.assign(style, getShapeStyle(element));

    const dynamicText = getContentFieldValue(element.contentField, content);
    const roleText = getText(element.role, content);

    // contentField가 지정된 요소는 실제 카드 데이터를 우선합니다.
    // 일부 기존 템플릿은 highlight/title/body 요소에 contentField가 빠져 있어도
    // 카드 데이터가 존재하므로, 역할에 해당하는 실제 값도 우선 반영합니다.
    const text = dynamicText || roleText || element.text || '';

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
