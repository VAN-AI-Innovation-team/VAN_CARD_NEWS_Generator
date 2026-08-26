import type {
  CardGenerationResult,
  LayoutCard,
  LayoutElement,
  PreviewImage,
  PreviewTemplate,
} from '../../api/contentApi';

import { getCropImageStyle } from './cropUtils';

import './CardNewsPreview.css';

interface CardNewsPreviewProps {
  template: PreviewTemplate;
  cardGenerationResult: CardGenerationResult;
  images: PreviewImage[];
  cardIndex: number;
  editingField?: string | null;
  onStartEdit?: (field: string) => void;
  onTextChange?: (field: string, value: string) => void;
  onFinishEdit?: () => void;
}

interface RenderContext {
  template: PreviewTemplate;
  content:
    | CardGenerationResult['cover']
    | CardGenerationResult['content'][number]
    | CardGenerationResult['closing'];
  images: PreviewImage[];
}

const SHAPE_ROLES = new Set(['background', 'overlay', 'panel', 'divider']);

const TEXT_ROLES = new Set([
  'title',
  'body',
  'highlight',
  'eyebrow',
  'footer',
  'badge',
  'decoration',
  'cta',
  'date',
  'location',
]);

const EDITABLE_FIELDS = new Set([
  'title',
  'body',
  'highlight',
  'date',
  'location',
  'cta',
]);

function getTokenValue(tokens: Record<string, string>, token?: string) {
  return token ? tokens[token] : undefined;
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

function getRoleText(role: string, content: RenderContext['content']): string {
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
) {
  if (!('imageId' in content) || content.imageId == null) {
    return undefined;
  }

  return images.find((image) => image.id === content.imageId);
}

function getTypographyStyle(
  typography: Record<string, string>,
  typographyToken: string | undefined,
  canvasWidth: number,
): React.CSSProperties {
  if (!typographyToken || !canvasWidth) {
    return {};
  }

  const toCqw = (raw?: string) => {
    if (!raw) {
      return undefined;
    }

    const numeric = Number.parseFloat(raw);

    return Number.isNaN(numeric)
      ? undefined
      : `${(numeric / canvasWidth) * 100}cqw`;
  };

  const style: React.CSSProperties = {};

  const fontSize = toCqw(typography[`${typographyToken}Size`]);

  const letterSpacing = toCqw(typography[`${typographyToken}LetterSpacing`]);

  if (fontSize) {
    style.fontSize = fontSize;
  }

  if (letterSpacing) {
    style.letterSpacing = letterSpacing;
  }

  const fontWeight = typography[`${typographyToken}Weight`];

  if (fontWeight) {
    style.fontWeight = fontWeight as React.CSSProperties['fontWeight'];
  }

  const lineHeight = typography[`${typographyToken}LineHeight`];

  if (lineHeight) {
    style.lineHeight = lineHeight;
  }

  return style;
}

function getShapeStyle(element: LayoutElement): React.CSSProperties {
  if (element.shape === 'pill') {
    return {
      borderRadius: '999px',
    };
  }

  if (element.shape === 'badge' || element.shape === 'chip') {
    return {
      borderRadius: '1.4cqw',
    };
  }

  return {};
}

/**
 * ImageCropEditor의 LIVE CROP PREVIEW와
 * 동일한 공통 crop 계산을 사용합니다.
 */
function getImageCropStyle(
  content: RenderContext['content'],
): React.CSSProperties {
  if (!('cropArea' in content) || !content.cropArea) {
    return {};
  }

  return getCropImageStyle(content.cropArea);
}

function getElementText(
  element: LayoutElement,
  content: RenderContext['content'],
): string {
  if (element.contentField) {
    return getContentFieldValue(element.contentField, content);
  }

  if (EDITABLE_FIELDS.has(element.role)) {
    return getRoleText(element.role, content);
  }

  return element.text ?? '';
}

function renderElement(
  elementKey: string,
  element: LayoutElement,
  context: RenderContext,
  props: Pick<
    CardNewsPreviewProps,
    'editingField' | 'onStartEdit' | 'onTextChange' | 'onFinishEdit'
  >,
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

  if (!TEXT_ROLES.has(element.role)) {
    return null;
  }

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

  const field =
    element.contentField && EDITABLE_FIELDS.has(element.contentField)
      ? element.contentField
      : EDITABLE_FIELDS.has(element.role)
        ? element.role
        : null;

  const text = getElementText(element, content);

  const isEditing = field != null && props.editingField === field;

  if (isEditing && field) {
    const common = {
      value: text,
      maxLength: element.maxChars,
      autoFocus: true,
      onChange: (
        event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
      ) => props.onTextChange?.(field, event.target.value),
      onBlur: () => props.onFinishEdit?.(),
      onKeyDown: (
        event: React.KeyboardEvent<HTMLInputElement | HTMLTextAreaElement>,
      ) => {
        if (event.key === 'Escape') {
          props.onFinishEdit?.();
        }

        if (event.key === 'Enter' && field !== 'body') {
          event.preventDefault();
          props.onFinishEdit?.();
        }
      },
      onClick: (event: React.MouseEvent) => {
        event.stopPropagation();
      },
    };

    return (
      <div
        key={elementKey}
        className="card-news-preview__element card-news-preview__text card-news-preview__text--editing"
        style={style}
      >
        {field === 'body' ? (
          <textarea
            {...common}
            className="card-news-preview__inline-editor card-news-preview__inline-editor--textarea"
          />
        ) : (
          <input {...common} className="card-news-preview__inline-editor" />
        )}
      </div>
    );
  }

  return (
    <div
      key={elementKey}
      className={`card-news-preview__element card-news-preview__text card-news-preview__text--${element.role} ${
        field ? 'card-news-preview__text--editable' : ''
      }`}
      style={style}
      onClick={() => field && props.onStartEdit?.(field)}
      title={field ? '클릭해서 수정' : undefined}
    >
      {text}
    </div>
  );
}

export default function CardNewsPreview({
  template,
  cardGenerationResult,
  images,
  cardIndex,
  editingField,
  onStartEdit,
  onTextChange,
  onFinishEdit,
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
          renderElement(
            key,
            element,
            {
              template,
              content,
              images,
            },
            {
              editingField,
              onStartEdit,
              onTextChange,
              onFinishEdit,
            },
          ),
        )}
    </div>
  );
}
