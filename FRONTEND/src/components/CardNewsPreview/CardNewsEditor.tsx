import { useEffect, useMemo, useState } from 'react';

import type {
  CardGenerationResult,
  CardImagePlacement,
  ContentPreviewResponse,
  LayoutCard,
  PreviewImage,
} from '../../api/contentApi';

import {
  regenerateCard,
  updateCardImagePlacements,
  updateContentPreview,
  updateContentTemplate,
} from '../../api/contentApi';

import { useTemplate } from '../../contexts/TemplateContext';

import CardNewsPreview from './CardNewsPreview';
import ImageCropEditor from './ImageCropEditor';

import './CardNewsEditor.css';

interface CardNewsEditorProps {
  preview: ContentPreviewResponse;
  onUpdated: (preview: ContentPreviewResponse) => void;
  onGenerate: (preview: ContentPreviewResponse) => Promise<void>;
}

type EditableCard =
  | CardGenerationResult['cover']
  | CardGenerationResult['content'][number]
  | CardGenerationResult['closing'];

type EditableField =
  'title' | 'body' | 'highlight' | 'date' | 'location' | 'cta';

function cloneResult(result: CardGenerationResult): CardGenerationResult {
  return {
    cover: {
      ...result.cover,
      cropArea: result.cover.cropArea ? { ...result.cover.cropArea } : null,
    },

    content: result.content.map((card) => ({
      ...card,
      cropArea: card.cropArea ? { ...card.cropArea } : null,
    })),

    closing: {
      ...result.closing,
      cropArea: result.closing.cropArea ? { ...result.closing.cropArea } : null,
    },
  };
}

function cloneResultWithPlacements(
  result: CardGenerationResult,
  placements: CardImagePlacement[] | null,
): CardGenerationResult {
  const next = cloneResult(result);

  for (const placement of placements ?? []) {
    if (placement.cardType === 'cover' && placement.cardIndex === 0) {
      next.cover.imageId = placement.imageId;
      next.cover.cropArea = { ...placement.cropArea };
      continue;
    }

    if (placement.cardType === 'closing' && placement.cardIndex === 0) {
      next.closing.imageId = placement.imageId;
      next.closing.cropArea = { ...placement.cropArea };
      continue;
    }

    if (placement.cardType === 'content' && next.content[placement.cardIndex]) {
      next.content[placement.cardIndex].imageId = placement.imageId;

      next.content[placement.cardIndex].cropArea = {
        ...placement.cropArea,
      };
    }
  }

  return next;
}

/**
 * 총 카드 수 상한. 인스타그램 캐러셀 제약이며, 백엔드
 * `InstagramClient.MAX_CAROUSEL_ITEMS`와 같은 값이다(언어가 달라 공유 불가).
 */
const MAX_CARDS = 10;

/** 본문 카드 하한. 백엔드 `CardGenerationValidator`가 빈 content 배열을 거부한다. */
const MIN_CONTENT_CARDS = 1;

function createEmptyContentCard(): CardGenerationResult['content'][number] {
  return {
    title: '새 카드 제목',
    body: '새 카드 내용을 입력해주세요.',
    highlight: '',
    date: null,
    location: null,
    imageId: null,
    cropArea: null,
  };
}

/**
 * 현재 카드 인덱스에 대응하는 템플릿 레이아웃 카드를 반환합니다.
 *
 * 카드 인덱스 기준:
 * - 0                 → cover
 * - 1 ~ contentCount  → content
 * - contentCount + 1  → closing
 */
function getLayoutCard(
  template: ContentPreviewResponse['template'],
  cardIndex: number,
  contentCount: number,
): LayoutCard {
  if (cardIndex === 0) {
    return template.layout.cards.cover;
  }

  if (cardIndex <= contentCount) {
    return template.layout.cards.content;
  }

  return template.layout.cards.closing;
}

export default function CardNewsEditor({
  preview,
  onUpdated,
  onGenerate,
}: CardNewsEditorProps) {
  const { templates, reloadTemplates } = useTemplate();

  const [result, setResult] = useState<CardGenerationResult | null>(
    preview.cardGenerationResult
      ? cloneResultWithPlacements(
          preview.cardGenerationResult,
          preview.cardImagePlacements,
        )
      : null,
  );

  const [selectedCardIndex, setSelectedCardIndex] = useState(0);

  const [editingField, setEditingField] = useState<EditableField | null>(null);

  const [isSaving, setIsSaving] = useState(false);

  const [isGenerating, setIsGenerating] = useState(false);

  const [isRegeneratingCard, setIsRegeneratingCard] = useState(false);

  const [error, setError] = useState<string | null>(null);

  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [regenerationInstruction, setRegenerationInstruction] = useState('');

  useEffect(() => {
    void reloadTemplates(preview.template.contentType);
  }, [preview.template.contentType, reloadTemplates]);

  useEffect(() => {
    if (preview.cardGenerationResult) {
      setResult(
        cloneResultWithPlacements(
          preview.cardGenerationResult,
          preview.cardImagePlacements,
        ),
      );

      setSelectedCardIndex(0);
      setEditingField(null);
      setError(null);
    }
  }, [preview]);

  const cardCount = result ? result.content.length + 2 : 0;

  const selectedCard: EditableCard | null = useMemo(() => {
    if (!result) {
      return null;
    }

    if (selectedCardIndex === 0) {
      return result.cover;
    }

    if (selectedCardIndex <= result.content.length) {
      return result.content[selectedCardIndex - 1];
    }

    return result.closing;
  }, [result, selectedCardIndex]);

  function getSelectedImageId(): number | null {
    if (
      !selectedCard ||
      !('imageId' in selectedCard) ||
      selectedCard.imageId == null
    ) {
      return null;
    }

    return selectedCard.imageId;
  }

  function getSelectedCropArea() {
    if (
      !selectedCard ||
      !('cropArea' in selectedCard) ||
      !selectedCard.cropArea
    ) {
      return {
        x: 0,
        y: 0,
        width: 100,
        height: 100,
      };
    }

    return {
      ...selectedCard.cropArea,
    };
  }

  function updateSelectedCard(field: string, value: string) {
    setResult((current) => {
      if (!current) {
        return current;
      }

      const next = cloneResult(current);

      if (selectedCardIndex === 0) {
        next.cover = {
          ...next.cover,
          [field]: value,
        };
      } else if (selectedCardIndex <= next.content.length) {
        next.content[selectedCardIndex - 1] = {
          ...next.content[selectedCardIndex - 1],
          [field]: value,
        };
      } else {
        next.closing = {
          ...next.closing,
          [field]: value,
        };
      }

      return next;
    });
  }

  /**
   * 이미지 선택 상태 변경.
   *
   * 이미지 없음(null)을 선택해도
   * select는 계속 렌더링되며,
   * cropArea만 초기화합니다.
   *
   * 다시 imageId를 선택하면
   * selectedImage 계산이 즉시 변경되어
   * ImageCropEditor가 바로 복구됩니다.
   */
  function updateSelectedImage(imageId: number | null) {
    setResult((current) => {
      if (!current) {
        return current;
      }

      const next = cloneResult(current);

      if (selectedCardIndex === 0) {
        next.cover.imageId = imageId;
        next.cover.cropArea = null;
      } else if (selectedCardIndex <= next.content.length) {
        next.content[selectedCardIndex - 1].imageId = imageId;

        next.content[selectedCardIndex - 1].cropArea = null;
      } else {
        next.closing.imageId = imageId;
        next.closing.cropArea = null;
      }

      return next;
    });
  }

  function updateSelectedCropArea(
    cropArea: NonNullable<EditableCard['cropArea']>,
  ) {
    setResult((current) => {
      if (!current) {
        return current;
      }

      const next = cloneResult(current);

      if (selectedCardIndex === 0) {
        next.cover.cropArea = {
          ...cropArea,
        };
      } else if (selectedCardIndex <= next.content.length) {
        next.content[selectedCardIndex - 1].cropArea = {
          ...cropArea,
        };
      } else {
        next.closing.cropArea = {
          ...cropArea,
        };
      }

      return next;
    });
  }

  function buildPlacements(
    current: CardGenerationResult,
  ): CardImagePlacement[] {
    const placements: CardImagePlacement[] = [];

    const add = (
      cardType: CardImagePlacement['cardType'],
      cardIndex: number,
      card: {
        imageId: number | null;
        cropArea: CardImagePlacement['cropArea'] | null;
      },
    ) => {
      if (card.imageId == null) {
        return;
      }

      placements.push({
        cardType,
        cardIndex,
        imageId: card.imageId,
        cropArea: card.cropArea ?? {
          x: 0,
          y: 0,
          width: 100,
          height: 100,
        },
      });
    };

    add('cover', 0, current.cover);

    current.content.forEach((card, index) => {
      add('content', index, card);
    });

    add('closing', 0, current.closing);

    return placements;
  }

  async function persistEditorState(current: CardGenerationResult) {
    const textUpdated = await updateContentPreview(preview.contentId, current);

    const placementUpdated = await updateCardImagePlacements(
      preview.contentId,
      buildPlacements(current),
    );

    return placementUpdated ?? textUpdated;
  }

  function addContentCard() {
    if (cardCount >= MAX_CARDS) {
      return;
    }

    setResult((current) => {
      if (!current) {
        return current;
      }

      const next = cloneResult(current);

      next.content.push(createEmptyContentCard());

      return next;
    });

    setSelectedCardIndex(cardCount - 1);
  }

  function deleteSelectedContentCard() {
    if (
      !result ||
      selectedCardIndex === 0 ||
      selectedCardIndex > result.content.length ||
      result.content.length <= MIN_CONTENT_CARDS
    ) {
      return;
    }

    setResult((current) => {
      if (!current) {
        return current;
      }

      const next = cloneResult(current);

      next.content.splice(selectedCardIndex - 1, 1);

      return next;
    });

    setSelectedCardIndex((current) => Math.max(0, current - 1));
  }

  function moveSelectedCard(direction: 'up' | 'down') {
    if (
      !result ||
      selectedCardIndex === 0 ||
      selectedCardIndex > result.content.length
    ) {
      return;
    }

    setResult((current) => {
      if (!current) {
        return current;
      }

      const next = cloneResult(current);

      const index = selectedCardIndex - 1;

      const target = direction === 'up' ? index - 1 : index + 1;

      if (target < 0 || target >= next.content.length) {
        return next;
      }

      [next.content[index], next.content[target]] = [
        next.content[target],
        next.content[index],
      ];

      return next;
    });

    setSelectedCardIndex((current) =>
      direction === 'up' ? current - 1 : current + 1,
    );
  }

  async function handleSave() {
    if (!result) {
      return;
    }

    try {
      setIsSaving(true);
      setError(null);
      setSuccessMessage(null);

      const updated = await persistEditorState(result);

      onUpdated(updated);

      setSuccessMessage('수정 내용이 저장되었습니다.');
    } catch (e) {
      console.error(e);

      setError(
        e instanceof Error ? e.message : '수정 내용을 저장하지 못했습니다.',
      );
    } finally {
      setIsSaving(false);
    }
  }

  async function handleGenerate() {
    if (!result || isSaving || isGenerating) {
      return;
    }

    try {
      setIsGenerating(true);
      setError(null);
      setSuccessMessage(null);

      const updated = await persistEditorState(result);

      onUpdated(updated);

      await onGenerate(updated);
    } catch (e) {
      console.error(e);

      setError(
        e instanceof Error ? e.message : '카드뉴스를 생성하지 못했습니다.',
      );
    } finally {
      setIsGenerating(false);
    }
  }

  async function handleTemplateChange(templateId: number) {
    if (templateId === preview.template.id || isSaving || isGenerating) {
      return;
    }

    try {
      setIsSaving(true);
      setError(null);
      setSuccessMessage(null);

      const updated = await updateContentTemplate(
        preview.contentId,
        templateId,
      );

      onUpdated(updated);

      setResult(
        updated.cardGenerationResult
          ? cloneResultWithPlacements(
              updated.cardGenerationResult,
              updated.cardImagePlacements,
            )
          : null,
      );

      await reloadTemplates(preview.template.contentType);

      setSuccessMessage('템플릿이 변경되었습니다. 카드 내용은 유지됩니다.');
    } catch (e) {
      console.error(e);

      setError(
        e instanceof Error ? e.message : '템플릿을 변경하지 못했습니다.',
      );
    } finally {
      setIsSaving(false);
    }
  }

  async function handleRegenerateCard() {
    if (
      !regenerationInstruction.trim() ||
      !result ||
      isRegeneratingCard ||
      isSaving ||
      isGenerating
    ) {
      return;
    }

    const cardType =
      selectedCardIndex === 0
        ? 'cover'
        : selectedCardIndex <= result.content.length
          ? 'content'
          : 'closing';

    const cardIndex = cardType === 'content' ? selectedCardIndex - 1 : 0;

    try {
      setIsRegeneratingCard(true);
      setError(null);
      setSuccessMessage(null);

      const updated = await regenerateCard(preview.contentId, {
        cardType,
        cardIndex,
        instruction: regenerationInstruction.trim(),
      });

      onUpdated(updated);

      setResult(
        updated.cardGenerationResult
          ? cloneResultWithPlacements(
              updated.cardGenerationResult,
              updated.cardImagePlacements,
            )
          : null,
      );

      setRegenerationInstruction('');

      setSuccessMessage('선택한 카드만 AI로 다시 작성했습니다.');
    } catch (e) {
      console.error(e);

      setError(
        e instanceof Error ? e.message : '카드를 다시 작성하지 못했습니다.',
      );
    } finally {
      setIsRegeneratingCard(false);
    }
  }

  if (!result || !selectedCard) {
    return (
      <div className="card-news-editor__empty">
        카드 구성 결과를 기다리는 중입니다.
      </div>
    );
  }

  /*
   * ---------------------------------------------------------
   * 이미지 상태 검증
   * ---------------------------------------------------------
   *
   * 1. selectedCard.imageId
   * 2. preview.images
   * 3. 현재 카드 템플릿의 image element
   *
   * 세 조건을 모두 만족해야 실제 이미지 편집이 가능합니다.
   */

  const selectedImageId = getSelectedImageId();

  const selectedImage: PreviewImage | undefined =
    selectedImageId !== null
      ? preview.images.find((image) => image.id === selectedImageId)
      : undefined;

  /*
   * 현재 선택된 카드의 실제 템플릿 레이아웃을
   * 공통 함수로 조회합니다.
   *
   * 카드 인덱스:
   * 0 → cover
   * 1 ~ content.length → content
   * 마지막 → closing
   */
  const currentLayoutCard = getLayoutCard(
    preview.template,
    selectedCardIndex,
    result.content.length,
  );

  const hasTemplateImageElement = Object.values(
    currentLayoutCard.elements,
  ).some((element) => element.role === 'image');

  const hasUsableSelectedImage =
    selectedImageId !== null &&
    selectedImage !== undefined &&
    hasTemplateImageElement;

  return (
    <div className="card-news-editor">
      <div className="card-news-editor__cards">
        {Array.from({ length: cardCount }, (_, index) => (
          <button
            key={index}
            type="button"
            className={`card-news-editor__card-button ${
              selectedCardIndex === index
                ? 'card-news-editor__card-button--selected'
                : ''
            }`}
            onClick={() => {
              setSelectedCardIndex(index);
              setEditingField(null);
            }}
          >
            <span>CARD {String(index + 1).padStart(2, '0')}</span>

            {index === 0 && <small>COVER</small>}

            {index === cardCount - 1 && <small>CLOSING</small>}
          </button>
        ))}

        <button
          type="button"
          className="card-news-editor__add-card"
          onClick={addContentCard}
          disabled={cardCount >= MAX_CARDS}
        >
          + 카드 추가
        </button>

        {cardCount >= MAX_CARDS && (
          <span className="card-news-editor__card-limit">
            인스타그램 캐러셀은 최대 {MAX_CARDS}장까지 게시할 수 있습니다.
          </span>
        )}
      </div>

      <div className="card-news-editor__workspace">
        <div className="card-news-editor__preview-panel">
          <div className="card-news-editor__preview-header">
            <div>
              <span>LIVE EDITOR</span>

              <strong>
                CARD {String(selectedCardIndex + 1).padStart(2, '0')}
              </strong>
            </div>

            <span>텍스트를 클릭해서 직접 수정</span>
          </div>

          <div className="card-news-editor__preview">
            <CardNewsPreview
              template={preview.template}
              cardGenerationResult={result}
              images={preview.images}
              cardIndex={selectedCardIndex}
              editingField={editingField}
              onStartEdit={(field) => setEditingField(field as EditableField)}
              onTextChange={updateSelectedCard}
              onFinishEdit={() => setEditingField(null)}
            />
          </div>
        </div>

        <div className="card-news-editor__tools">
          <div className="card-news-editor__tool-section">
            <div className="card-news-editor__tool-header">
              <h4>카드 편집</h4>
              <span>직접 수정</span>
            </div>

            <p className="card-news-editor__hint">
              카드 안의 제목·본문·강조 문구·날짜·장소·CTA를 클릭하면 바로 수정할
              수 있습니다.
            </p>

            {selectedCardIndex > 0 &&
              selectedCardIndex <= result.content.length && (
                <div className="card-news-editor__move-buttons">
                  <button
                    type="button"
                    onClick={() => moveSelectedCard('up')}
                    disabled={selectedCardIndex === 1}
                  >
                    ↑ 앞 카드
                  </button>

                  <button
                    type="button"
                    onClick={() => moveSelectedCard('down')}
                    disabled={selectedCardIndex === result.content.length}
                  >
                    ↓ 뒤 카드
                  </button>

                  <button
                    type="button"
                    className="card-news-editor__delete"
                    onClick={deleteSelectedContentCard}
                    disabled={result.content.length <= MIN_CONTENT_CARDS}
                  >
                    카드 삭제
                  </button>
                </div>
              )}
          </div>

          <div className="card-news-editor__tool-section">
            <div className="card-news-editor__tool-header">
              <h4>템플릿</h4>
              <span>디자인만 변경</span>
            </div>

            <select
              className="card-news-editor__tool-select"
              value={preview.template.id}
              disabled={isSaving || isGenerating}
              onChange={(event) =>
                void handleTemplateChange(Number(event.target.value))
              }
            >
              {templates
                .filter(
                  (template) =>
                    template.contentType === preview.template.contentType,
                )
                .map((template) => (
                  <option key={template.id} value={template.id}>
                    {template.name}
                  </option>
                ))}
            </select>

            <p className="card-news-editor__hint">
              템플릿을 바꿔도 현재 카드 내용은 유지됩니다. 생성된 이미지는 새
              템플릿 기준으로 다시 만들어야 합니다.
            </p>
          </div>

          <div className="card-news-editor__tool-section">
            <div className="card-news-editor__tool-header">
              <h4>선택 카드 AI 재작성</h4>
              <span>현재 카드만 변경</span>
            </div>

            <textarea
              className="card-news-editor__ai-input"
              value={regenerationInstruction}
              onChange={(event) =>
                setRegenerationInstruction(event.target.value)
              }
              placeholder="예: 본문을 더 간결하고 학생들이 이해하기 쉽게 바꿔줘"
              rows={4}
            />

            <button
              type="button"
              className="card-news-editor__ai-button"
              disabled={
                !regenerationInstruction.trim() ||
                isRegeneratingCard ||
                isSaving ||
                isGenerating
              }
              onClick={() => void handleRegenerateCard()}
            >
              {isRegeneratingCard ? 'AI 재작성 중...' : '이 카드만 AI 재작성'}
            </button>
          </div>

          <div className="card-news-editor__tool-section card-news-editor__crop-section">
            <div className="card-news-editor__tool-header">
              <h4>이미지 편집</h4>
              <span>크롭</span>
            </div>

            {/* select는 이미지가 없어도 절대 제거하지 않습니다. */}
            <select
              className="card-news-editor__tool-select"
              value={selectedImageId === null ? '' : String(selectedImageId)}
              onChange={(event) =>
                updateSelectedImage(
                  event.target.value === '' ? null : Number(event.target.value),
                )
              }
            >
              <option value="">이미지 없음</option>

              {preview.images.map((image, index) => (
                <option key={image.id} value={image.id}>
                  이미지 {index + 1}
                </option>
              ))}
            </select>

            {/*
             * imageId + preview.images + template image element
             * 세 가지가 모두 유효해야 크롭 편집기를 보여줍니다.
             *
             * 어느 하나라도 없으면 select는 유지하고
             * 아래 메시지를 보여줍니다.
             */}
            {hasUsableSelectedImage ? (
              <ImageCropEditor
                image={selectedImage}
                cropArea={getSelectedCropArea()}
                onChange={updateSelectedCropArea}
              />
            ) : (
              <div className="card-news-editor__no-image">
                이미지가 존재하지 않습니다.
              </div>
            )}

            {/*
             * 디버깅/상태 검증용 정보.
             * 사용자에게 불필요하게 보이지 않도록
             * 화면에는 표시하지 않고 DOM attribute로만 남깁니다.
             */}
            <div
              hidden
              data-selected-image-id={selectedImageId ?? ''}
              data-preview-image-found={selectedImage ? 'true' : 'false'}
              data-template-image-element={
                hasTemplateImageElement ? 'true' : 'false'
              }
            />
          </div>

          {error && <p className="card-news-editor__error">{error}</p>}

          {successMessage && (
            <p className="card-news-editor__success">{successMessage}</p>
          )}

          <div className="card-news-editor__actions">
            <button
              type="button"
              className="card-news-editor__save card-news-editor__save--secondary"
              disabled={isSaving || isGenerating}
              onClick={() => void handleSave()}
            >
              {isSaving ? '저장 중...' : '수정 내용 저장'}
            </button>

            <button
              type="button"
              className="card-news-editor__save"
              disabled={isSaving || isGenerating}
              onClick={() => void handleGenerate()}
            >
              {isGenerating ? '카드뉴스 생성 중...' : '카드뉴스 만들기'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
