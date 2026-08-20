import { useEffect, useState } from 'react';

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
}

type EditableCard =
  | CardGenerationResult['cover']
  | CardGenerationResult['content'][number]
  | CardGenerationResult['closing'];

function cloneResult(result: CardGenerationResult): CardGenerationResult {
  return {
    cover: {
      ...result.cover,
    },
    content: result.content.map((card) => ({
      ...card,
    })),
    closing: {
      ...result.closing,
    },
  };
}

export default function CardNewsEditor({
  preview,
  onUpdated,
}: CardNewsEditorProps) {
  const initialResult = preview.cardGenerationResult;

  const [result, setResult] = useState<CardGenerationResult>(
    initialResult
      ? cloneResult(initialResult)
      : {
          cover: {
            title: '',
            highlight: '',
          },
          content: [],
          closing: {
            cta: '',
          },
        },
  );

  const [selectedCardIndex, setSelectedCardIndex] = useState(0);

  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (preview.cardGenerationResult) {
      setResult(cloneResult(preview.cardGenerationResult));
      setSelectedCardIndex(0);
    }
  }, [preview]);

  const cardCount = result.content.length + 2;

  function getSelectedCard(): EditableCard {
    if (selectedCardIndex === 0) {
      return result.cover;
    }

    if (selectedCardIndex <= result.content.length) {
      return result.content[selectedCardIndex - 1];
    }

    return result.closing;
  }

  function updateSelectedCard(field: string, value: string) {
    setResult((current) => {
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

    if (field === 'cta' && 'cta' in card) {
      return card.cta ?? '';
    }

    return '';
  }

  async function handleSave() {
    try {
      setIsSaving(true);
      setError(null);

      const updatedPreview = await updateContentPreview(
        preview.contentId,
        result,
      );

      onUpdated(updatedPreview);
    } catch (saveError) {
      console.error('카드 구성 수정 저장 실패:', saveError);

      setError('수정 내용을 저장하지 못했습니다.');
    } finally {
      setIsSaving(false);
    }
  }

  const selectedCard = getSelectedCard();

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
            CARD {String(index + 1).padStart(2, '0')}
          </button>
        ))}
      </div>

      <div className="card-news-editor__workspace">
        <div className="card-news-editor__preview">
          <CardNewsPreview
            template={preview.template}
            cardGenerationResult={result}
            images={preview.images}
            cardIndex={selectedCardIndex}
          />
        </div>

        <div className="card-news-editor__form">
          <h4 className="card-news-editor__form-title">카드 내용 수정</h4>

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
                rows={7}
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

          {'cta' in selectedCard && (
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

          {error && <p className="card-news-editor__error">{error}</p>}

          <button
            type="button"
            className="card-news-editor__save"
            disabled={isSaving}
            onClick={handleSave}
          >
            {isSaving ? '저장 중...' : '수정 내용 저장'}
          </button>
        </div>
      </div>
    </div>
  );
}
