import { useEffect, useMemo, useState } from 'react';

import type {
  CardGenerationResult,
  ContentPreviewResponse,
} from '../../api/contentApi';

import { updateContentPreview } from '../../api/contentApi';

import CardNewsPreview from './CardNewsPreview';

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
      imageId: result.closing.imageId ?? null,
      cropArea: result.closing.cropArea ? { ...result.closing.cropArea } : null,
    },
  };
}

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

export default function CardNewsEditor({
  preview,
  onUpdated,
  onGenerate,
}: CardNewsEditorProps) {
  const [result, setResult] = useState<CardGenerationResult | null>(
    preview.cardGenerationResult
      ? cloneResult(preview.cardGenerationResult)
      : null,
  );

  const [selectedCardIndex, setSelectedCardIndex] = useState(0);

  const [isSaving, setIsSaving] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  useEffect(() => {
    if (preview.cardGenerationResult) {
      setResult(cloneResult(preview.cardGenerationResult));
      setSelectedCardIndex(0);
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

  function getSelectedLayoutCard() {
    if (selectedCardIndex === 0) {
      return preview.template.layout.cards.cover;
    }

    if (selectedCardIndex <= (result?.content.length ?? 0)) {
      return preview.template.layout.cards.content;
    }

    return preview.template.layout.cards.closing;
  }

  function usesContentField(field: 'date' | 'location' | 'cta'): boolean {
    const elements = getSelectedLayoutCard().elements;

    return Object.values(elements).some(
      (element) => element.contentField === field,
    );
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

        return next;
      }

      if (selectedCardIndex <= next.content.length) {
        const contentIndex = selectedCardIndex - 1;

        next.content[contentIndex] = {
          ...next.content[contentIndex],
          [field]: value,
        };

        return next;
      }

      next.closing = {
        ...next.closing,
        [field]: value,
      };

      return next;
    });
  }

  function updateSelectedImage(imageId: number | null) {
    setResult((current) => {
      if (!current) {
        return current;
      }

      const next = cloneResult(current);

      if (selectedCardIndex === 0) {
        next.cover.imageId = imageId;
        next.cover.cropArea = null;

        return next;
      }

      if (selectedCardIndex <= next.content.length) {
        const contentIndex = selectedCardIndex - 1;

        next.content[contentIndex].imageId = imageId;
        next.content[contentIndex].cropArea = null;

        return next;
      }

      next.closing.imageId = imageId;
      next.closing.cropArea = null;

      return next;
    });
  }

  function getFieldValue(card: EditableCard, field: string): string {
    if (field === 'title' && 'title' in card) {
      return card.title ?? '';
    }

    if (field === 'body' && 'body' in card) {
      return card.body ?? '';
    }

    if (field === 'highlight' && 'highlight' in card) {
      return card.highlight ?? '';
    }

    if (field === 'date' && 'date' in card) {
      return card.date ?? '';
    }

    if (field === 'location' && 'location' in card) {
      return card.location ?? '';
    }

    if (field === 'cta' && 'cta' in card) {
      return card.cta ?? '';
    }

    return '';
  }

  function getSelectedImageId(): number | null {
    if (!selectedCard) {
      return null;
    }

    if ('imageId' in selectedCard && selectedCard.imageId != null) {
      return selectedCard.imageId;
    }

    return null;
  }

  function addContentCard() {
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
      selectedCardIndex === 0 ||
      !result ||
      selectedCardIndex > result.content.length
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

      const targetIndex = direction === 'up' ? index - 1 : index + 1;

      if (targetIndex < 0 || targetIndex >= next.content.length) {
        return next;
      }

      [next.content[index], next.content[targetIndex]] = [
        next.content[targetIndex],
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

      const updatedPreview = await updateContentPreview(
        preview.contentId,
        result,
      );

      onUpdated(updatedPreview);

      setSuccessMessage('수정 내용이 저장되었습니다.');
    } catch (saveError) {
      console.error('카드 구성 수정 저장 실패:', saveError);

      setError('수정 내용을 저장하지 못했습니다.');
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

      const updatedPreview = await updateContentPreview(
        preview.contentId,
        result,
      );

      onUpdated(updatedPreview);
      await onGenerate(updatedPreview);
    } catch (generateError) {
      console.error('카드뉴스 생성 요청 실패:', generateError);
      setError(
        generateError instanceof Error
          ? generateError.message
          : '카드뉴스 생성 요청에 실패했습니다.',
      );
    } finally {
      setIsGenerating(false);
    }
  }

  if (!result || !selectedCard) {
    return (
      <div className="card-news-editor__empty">
        카드 구성 결과를 기다리는 중입니다.
      </div>
    );
  }

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
            onClick={() => setSelectedCardIndex(index)}
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
        >
          + 카드 추가
        </button>
      </div>

      <div className="card-news-editor__workspace">
        <div className="card-news-editor__preview-panel">
          <div className="card-news-editor__preview-header">
            <div>
              <span>LIVE PREVIEW</span>

              <strong>
                CARD {String(selectedCardIndex + 1).padStart(2, '0')}
              </strong>
            </div>

            <span>
              {preview.template.canvasWidth} × {preview.template.canvasHeight}
            </span>
          </div>

          <div className="card-news-editor__preview">
            <CardNewsPreview
              template={preview.template}
              cardGenerationResult={result}
              images={preview.images}
              cardIndex={selectedCardIndex}
            />
          </div>
        </div>

        <div className="card-news-editor__form">
          <div className="card-news-editor__form-header">
            <div>
              <h4>카드 내용 수정</h4>

              <p>수정한 내용은 미리보기에 즉시 반영됩니다.</p>
            </div>

            {selectedCardIndex > 0 &&
              selectedCardIndex <= result.content.length && (
                <div className="card-news-editor__move-buttons">
                  <button
                    type="button"
                    onClick={() => moveSelectedCard('up')}
                    disabled={selectedCardIndex === 1}
                  >
                    ↑
                  </button>

                  <button
                    type="button"
                    onClick={() => moveSelectedCard('down')}
                    disabled={selectedCardIndex === result.content.length}
                  >
                    ↓
                  </button>

                  <button
                    type="button"
                    className="card-news-editor__delete"
                    aria-label="선택한 카드 삭제"
                    title="카드 삭제"
                    onClick={deleteSelectedContentCard}
                  >
                    ×
                  </button>
                </div>
              )}
          </div>

          {'title' in selectedCard && (
            <label className="card-news-editor__field">
              <span>제목</span>

              <input
                type="text"
                value={getFieldValue(selectedCard, 'title')}
                onChange={(event) =>
                  updateSelectedCard('title', event.target.value)
                }
              />
            </label>
          )}

          {'body' in selectedCard && (
            <label className="card-news-editor__field">
              <span>본문</span>

              <textarea
                value={getFieldValue(selectedCard, 'body')}
                onChange={(event) =>
                  updateSelectedCard('body', event.target.value)
                }
                rows={8}
              />
            </label>
          )}

          {'highlight' in selectedCard && (
            <label className="card-news-editor__field">
              <span>강조 문구</span>

              <input
                type="text"
                value={getFieldValue(selectedCard, 'highlight')}
                onChange={(event) =>
                  updateSelectedCard('highlight', event.target.value)
                }
              />
            </label>
          )}

          {usesContentField('date') && 'date' in selectedCard && (
            <label className="card-news-editor__field">
              <span>날짜</span>

              <input
                type="text"
                placeholder="xxxx.xx.xx"
                value={getFieldValue(selectedCard, 'date')}
                onChange={(event) =>
                  updateSelectedCard('date', event.target.value)
                }
              />
            </label>
          )}

          {usesContentField('location') && 'location' in selectedCard && (
            <label className="card-news-editor__field">
              <span>장소</span>

              <input
                type="text"
                placeholder="장소를 넣어주세요"
                value={getFieldValue(selectedCard, 'location')}
                onChange={(event) =>
                  updateSelectedCard('location', event.target.value)
                }
              />
            </label>
          )}

          {usesContentField('cta') && 'cta' in selectedCard && (
            <label className="card-news-editor__field">
              <span>CTA</span>

              <input
                type="text"
                value={getFieldValue(selectedCard, 'cta')}
                onChange={(event) =>
                  updateSelectedCard('cta', event.target.value)
                }
              />
            </label>
          )}

          {'imageId' in selectedCard && (
            <label className="card-news-editor__field">
              <span>사용 이미지</span>

              <select
                value={getSelectedImageId() ?? ''}
                onChange={(event) => {
                  const value = event.target.value;

                  updateSelectedImage(value === '' ? null : Number(value));
                }}
              >
                <option value="">이미지 없음</option>

                {preview.images.map((image, index) => (
                  <option key={image.id} value={image.id}>
                    이미지 {index + 1}
                  </option>
                ))}
              </select>
            </label>
          )}

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
