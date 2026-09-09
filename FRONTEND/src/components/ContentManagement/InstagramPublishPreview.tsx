import { useEffect, useRef, useState } from 'react';
import {
  fetchInstagramPublishCaption,
  fetchInstagramPublishStatus,
  publishApprovedContentToInstagram,
  saveInstagramPublishCaption,
  type InstagramPublishResponse,
} from '../../api/approvalApi';
import type { GeneratedCardImageResponse } from '../../api/contentApi';
import './InstagramPublishPreview.css';

/** 백엔드 PublishPreflightValidator의 상한과 같은 값이다(언어가 달라 공유할 수 없다). */
const MAX_CAPTION_LENGTH = 2200;
const MAX_HASHTAGS = 30;

/** 결과가 확정되지 않은 상태 — 이 동안만 폴링한다. */
const IN_FLIGHT = ['SCHEDULED', 'PENDING', 'PROCESSING'];

const POLL_INTERVAL_MS = 3000;

interface Props {
  contentId: number;
  title: string;
  images: GeneratedCardImageResponse[];
  onBack: () => void;
  onPublished: () => void;
}

function countHashtags(caption: string) {
  return caption.match(/#[\p{L}\p{N}_]+/gu)?.length ?? 0;
}

function elapsedLabel(seconds: number) {
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(
    seconds % 60,
  ).padStart(2, '0')}`;
}

export default function InstagramPublishPreview({
  contentId,
  title,
  images,
  onBack,
  onPublished,
}: Props) {
  const [caption, setCaption] = useState('');
  const [savedCaption, setSavedCaption] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);
  const [record, setRecord] = useState<InstagramPublishResponse | null>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [error, setError] = useState<string | null>(null);

  // 발행이 끝난 순간을 한 번만 상위에 알리기 위한 표시. 폴링이 계속 돌아도 중복 통보하지 않는다.
  const notifiedRef = useRef(false);

  const inFlight = record !== null && IN_FLIGHT.includes(record.status);
  const published = record?.status === 'SUCCESS';
  const isDirty = caption !== savedCaption;
  const hashtags = countHashtags(caption);
  const tooLong = caption.length > MAX_CAPTION_LENGTH;
  const tooManyHashtags = hashtags > MAX_HASHTAGS;

  useEffect(() => {
    let canceled = false;

    async function load() {
      try {
        // 이미 발행한 콘텐츠를 다시 열었을 때도 그 상태가 보여야 한다.
        const [loadedCaption, latest] = await Promise.all([
          fetchInstagramPublishCaption(contentId),
          fetchInstagramPublishStatus(contentId),
        ]);

        if (canceled) {
          return;
        }

        setCaption(loadedCaption);
        setSavedCaption(loadedCaption);
        setRecord(latest);
      } catch (loadError) {
        if (!canceled) {
          setError(
            loadError instanceof Error
              ? loadError.message
              : '발행 미리보기를 불러오지 못했습니다.',
          );
        }
      } finally {
        if (!canceled) {
          setIsLoading(false);
        }
      }
    }

    void load();

    return () => {
      canceled = true;
    };
  }, [contentId]);

  // 진행 중인 동안만 상태를 되묻는다. 발행 한 건은 컨테이너 폴링 때문에 몇 분이 걸린다.
  useEffect(() => {
    if (!inFlight) {
      return;
    }

    const timer = window.setInterval(async () => {
      try {
        const latest = await fetchInstagramPublishStatus(contentId);

        if (latest) {
          setRecord(latest);
        }
      } catch {
        // 폴링 실패는 화면을 망가뜨리지 않는다. 다음 회차가 다시 묻는다.
      }
    }, POLL_INTERVAL_MS);

    return () => window.clearInterval(timer);
  }, [inFlight, contentId]);

  // 경과 시간은 "멈춘 화면"과 "기다리는 화면"을 구분해 주는 유일한 신호다.
  useEffect(() => {
    if (!inFlight) {
      return;
    }

    const timer = window.setInterval(() => {
      setElapsedSeconds((seconds) => seconds + 1);
    }, 1000);

    return () => window.clearInterval(timer);
  }, [inFlight]);

  useEffect(() => {
    if (published && !notifiedRef.current) {
      notifiedRef.current = true;
      onPublished();
    }
  }, [published, onPublished]);

  async function save() {
    if (isSaving || !isDirty) {
      return;
    }

    try {
      setIsSaving(true);
      setError(null);

      setSavedCaption(await saveInstagramPublishCaption(contentId, caption));
    } catch (saveError) {
      setError(
        saveError instanceof Error
          ? saveError.message
          : '캡션 저장에 실패했습니다.',
      );
    } finally {
      setIsSaving(false);
    }
  }

  async function publish() {
    if (isPublishing || inFlight || published) {
      return;
    }

    try {
      setIsPublishing(true);
      setError(null);
      setElapsedSeconds(0);

      // 저장하지 않고 발행해도 화면에 보이는 문구가 나가야 한다. 저장이 곧 발행할 문구의 확정이다.
      if (isDirty) {
        setSavedCaption(await saveInstagramPublishCaption(contentId, caption));
      }

      setRecord(await publishApprovedContentToInstagram(contentId));
    } catch (publishError) {
      setError(
        publishError instanceof Error
          ? publishError.message
          : 'Instagram 발행 요청에 실패했습니다.',
      );
    } finally {
      setIsPublishing(false);
    }
  }

  const currentImage = images[selectedIndex];

  return (
    <section className="ig-preview">
      <div className="ig-preview__topbar">
        <button type="button" className="ig-preview__back" onClick={onBack}>
          ← 콘텐츠 상세
        </button>

        <span className="ig-preview__eyebrow">INSTAGRAM · PREVIEW</span>
      </div>

      {error && (
        <div className="ig-preview__error" role="alert">
          {error}
        </div>
      )}

      {isLoading ? (
        <div className="ig-preview__loading">
          <div className="ig-preview__spinner" />
          <strong>미리보기를 불러오는 중입니다.</strong>
        </div>
      ) : (
        <div className="ig-preview__layout">
          <article className="ig-post" aria-label="인스타그램 게시물 미리보기">
            <header className="ig-post__header">
              <span className="ig-post__avatar" aria-hidden="true" />

              <div className="ig-post__account">
                <strong>{title}</strong>
                <span>게시물 미리보기</span>
              </div>
            </header>

            <div className="ig-post__media">
              {currentImage ? (
                <img
                  src={currentImage.imageUrl}
                  alt={`카드 ${selectedIndex + 1}`}
                />
              ) : (
                <div className="ig-post__media-empty">
                  카드 이미지가 없습니다.
                </div>
              )}

              {images.length > 1 && (
                <>
                  <button
                    type="button"
                    className="ig-post__nav ig-post__nav--prev"
                    onClick={() =>
                      setSelectedIndex(
                        (index) => (index - 1 + images.length) % images.length,
                      )
                    }
                    aria-label="이전 카드"
                  >
                    ‹
                  </button>

                  <button
                    type="button"
                    className="ig-post__nav ig-post__nav--next"
                    onClick={() =>
                      setSelectedIndex((index) => (index + 1) % images.length)
                    }
                    aria-label="다음 카드"
                  >
                    ›
                  </button>

                  <span className="ig-post__counter">
                    {selectedIndex + 1}/{images.length}
                  </span>
                </>
              )}
            </div>

            {images.length > 1 && (
              <div className="ig-post__dots" aria-hidden="true">
                {images.map((image, index) => (
                  <span
                    key={image.id}
                    className={`ig-post__dot ${
                      index === selectedIndex ? 'ig-post__dot--active' : ''
                    }`}
                  />
                ))}
              </div>
            )}

            <div className="ig-post__caption">
              <p className="ig-post__caption-preview">
                <strong>{title}</strong> {caption}
              </p>
            </div>
          </article>

          <div className="ig-preview__editor">
            <label className="ig-preview__label" htmlFor="ig-caption">
              캡션 · 해시태그
            </label>

            <textarea
              id="ig-caption"
              className="ig-preview__textarea"
              value={caption}
              onChange={(event) => setCaption(event.target.value)}
              disabled={inFlight || published}
              rows={16}
            />

            <div className="ig-preview__counters">
              <span className={tooLong ? 'ig-preview__counter--over' : ''}>
                {caption.length} / {MAX_CAPTION_LENGTH}자
              </span>

              <span
                className={tooManyHashtags ? 'ig-preview__counter--over' : ''}
              >
                해시태그 {hashtags} / {MAX_HASHTAGS}개
              </span>
            </div>

            {published ? (
              <div className="ig-preview__status ig-preview__status--success">
                <strong>발행이 완료되었습니다.</strong>

                {record?.permalink && (
                  <a href={record.permalink} target="_blank" rel="noreferrer">
                    인스타그램에서 보기 →
                  </a>
                )}
              </div>
            ) : inFlight ? (
              <div className="ig-preview__status ig-preview__status--progress">
                <div className="ig-preview__spinner" />

                <div>
                  <strong>
                    {record?.status === 'PROCESSING'
                      ? '인스타그램에 올리는 중입니다.'
                      : '발행을 준비하는 중입니다.'}
                  </strong>

                  <span>
                    보통 1~5분 걸립니다. 이 화면을 열어 두면 완료되는 대로
                    바뀝니다. · 경과 {elapsedLabel(elapsedSeconds)}
                  </span>
                </div>
              </div>
            ) : record?.status === 'FAILED' ? (
              <div className="ig-preview__status ig-preview__status--failed">
                <strong>발행에 실패했습니다.</strong>
                <span>{record.errorMessage ?? '원인을 알 수 없습니다.'}</span>
              </div>
            ) : null}

            <div className="ig-preview__actions">
              <button
                type="button"
                className="secondary-button"
                onClick={() => void save()}
                disabled={!isDirty || isSaving || inFlight || published}
              >
                {isSaving ? '저장 중...' : isDirty ? '캡션 저장' : '저장됨'}
              </button>

              <button
                type="button"
                className="primary-button"
                onClick={() => void publish()}
                disabled={
                  isPublishing ||
                  inFlight ||
                  published ||
                  tooLong ||
                  tooManyHashtags ||
                  !images.length
                }
              >
                {published
                  ? '발행 완료'
                  : inFlight
                    ? '발행 중...'
                    : isPublishing
                      ? '요청 중...'
                      : record?.status === 'FAILED'
                        ? '다시 발행'
                        : '인스타그램 발행'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
