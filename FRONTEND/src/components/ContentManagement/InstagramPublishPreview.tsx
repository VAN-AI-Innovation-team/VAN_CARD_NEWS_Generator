import { useEffect, useRef, useState } from 'react';
import {
  cancelInstagramSchedule,
  fetchInstagramPublishCaption,
  fetchInstagramPublishStatus,
  publishApprovedContentToInstagram,
  runInstagramPublishNow,
  saveInstagramPublishCaption,
  scheduleInstagramPublish,
  type InstagramPublishResponse,
} from '../../api/approvalApi';
import type { GeneratedCardImageResponse } from '../../api/contentApi';
import avatar from '../../assets/instagram-avatar.png';
import './InstagramPublishPreview.css';

/** 발행 대상 계정. 미리보기에 보이는 이름과 캡션 앞 이름은 실제 계정이어야 한다. */
const ACCOUNT = 'veritas_van';

/** 백엔드 PublishPreflightValidator의 상한과 같은 값이다(언어가 달라 공유할 수 없다). */
const MAX_CAPTION_LENGTH = 2200;
const MAX_HASHTAGS = 30;

/** 워커가 집어가 처리 중인 상태 — 이 동안만 경과 시간을 센다. */
const RUNNING = ['PENDING', 'PROCESSING'];

const POLL_INTERVAL_MS = 3000;

/** 서버 app.publish.schedule.min-lead-minutes와 같은 값. 이보다 이른 시각은 400으로 막힌다. */
const MIN_LEAD_MINUTES = 5;

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

/** date 입력이 읽는 형식(YYYY-MM-DD)의 로컬 날짜. UTC 변환으로 하루가 밀리지 않게 오프셋을 뺀다. */
function localInputValue(date: Date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 10);
}

function scheduleLabel(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** 해시태그만 인스타그램처럼 파랗게 보여 준다. 나머지는 입력한 그대로다. */
function renderCaption(caption: string) {
  return caption.split(/(#[\p{L}\p{N}_]+)/gu).map((part, index) =>
    part.startsWith('#') ? (
      <span key={index} className="ig-post__tag">
        {part}
      </span>
    ) : (
      part
    ),
  );
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
  const [isCanceling, setIsCanceling] = useState(false);
  const [mode, setMode] = useState<'now' | 'scheduled'>('now');
  const [scheduledDate, setScheduledDate] = useState('');
  const [scheduledTime, setScheduledTime] = useState('');
  const [record, setRecord] = useState<InstagramPublishResponse | null>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [error, setError] = useState<string | null>(null);

  // 발행이 끝난 순간을 한 번만 상위에 알리기 위한 표시. 폴링이 계속 돌아도 중복 통보하지 않는다.
  const notifiedRef = useRef(false);

  const scheduled = record?.status === 'SCHEDULED';
  const running = record !== null && RUNNING.includes(record.status);
  const published = record?.status === 'SUCCESS';
  // 예약·진행·완료 중에는 문구를 고칠 수 없다. 이미 확정된 문구가 나가기 때문이다.
  const locked = scheduled || running || published;
  const isDirty = caption !== savedCaption;
  const hashtags = countHashtags(caption);
  const tooLong = caption.length > MAX_CAPTION_LENGTH;
  const tooManyHashtags = hashtags > MAX_HASHTAGS;
  const earliest = localInputValue(
    new Date(Date.now() + MIN_LEAD_MINUTES * 60000),
  );
  // 서버는 한 문자열로 받는다. 날짜·시간을 나눠 입력받고 여기서만 합친다.
  const scheduledAt =
    scheduledDate && scheduledTime ? `${scheduledDate}T${scheduledTime}` : '';

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

  // 결과가 확정되지 않은 동안만 상태를 되묻는다. 발행 한 건은 컨테이너 폴링 때문에 몇 분이 걸린다.
  useEffect(() => {
    if (!scheduled && !running) {
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
  }, [scheduled, running, contentId]);

  // 경과 시간은 "멈춘 화면"과 "기다리는 화면"을 구분해 주는 유일한 신호다.
  useEffect(() => {
    if (!running) {
      return;
    }

    const timer = window.setInterval(() => {
      setElapsedSeconds((seconds) => seconds + 1);
    }, 1000);

    return () => window.clearInterval(timer);
  }, [running]);

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
    if (isPublishing || locked) {
      return;
    }

    if (mode === 'scheduled' && !scheduledAt) {
      setError('예약 발행 시각을 선택해 주세요.');
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

      // 예약은 큐에 시각만 걸어 두고 끝난다. 실행은 도래 시점에 워커가 한다.
      if (mode === 'scheduled') {
        setRecord(
          await scheduleInstagramPublish(contentId, scheduledAt, caption),
        );
        onPublished();
        return;
      }

      setRecord(await publishApprovedContentToInstagram(contentId));

      // 큐에 넣는 것만으로는 발행이 일어나지 않는다. 실행까지 요청해야 하고, 그 요청 안에서
      // 컨테이너 폴링이 끝나므로 응답까지 몇 분이 걸린다. 진행 상황은 이 응답이 아니라
      // 상태 폴링이 보여주므로 여기서 기다리지 않는다.
      void runInstagramPublishNow(contentId)
        .then(setRecord)
        .catch((runError: unknown) => {
          // 실행 요청이 끊겨도 건은 큐에 남아 회수·재시도 경로가 집어간다.
          // 화면을 실패로 바꾸지 않고 폴링이 보는 실제 상태를 따른다.
          console.warn('발행 실행 요청이 끝까지 가지 못했습니다.', runError);
        });
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

  async function cancelSchedule() {
    if (isCanceling || !scheduled) {
      return;
    }

    try {
      setIsCanceling(true);
      setError(null);

      setRecord(await cancelInstagramSchedule(contentId));
      onPublished();
    } catch (cancelError) {
      setError(
        cancelError instanceof Error
          ? cancelError.message
          : '예약 취소에 실패했습니다.',
      );
    } finally {
      setIsCanceling(false);
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
              <img className="ig-post__avatar" src={avatar} alt="" />

              <div className="ig-post__account">
                <strong>{ACCOUNT}</strong>
                <span>{title} · 게시물 미리보기</span>
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

            {/* 실제 게시물의 액션 줄. 미리보기라 동작하지는 않고 위치와 여백만 재현한다. */}
            <div className="ig-post__actions" aria-hidden="true">
              <svg viewBox="0 0 24 24" className="ig-post__icon">
                <path d="M16.8 3.6c-1.9 0-3.6 1.1-4.8 2.7-1.2-1.6-2.9-2.7-4.8-2.7C4 3.6 1.7 6 1.7 9.1c0 5 5.2 8.4 9.6 11.6l.7.5.7-.5c4.4-3.2 9.6-6.6 9.6-11.6 0-3.1-2.3-5.5-5.5-5.5z" />
              </svg>

              <svg viewBox="0 0 24 24" className="ig-post__icon">
                <path d="M20.7 2H3.3C2 2 1 3 1 4.3v11.4C1 17 2 18 3.3 18H6v3.4c0 .8.9 1.2 1.5.7L12.6 18h8.1c1.3 0 2.3-1 2.3-2.3V4.3C23 3 22 2 20.7 2z" />
              </svg>

              <svg viewBox="0 0 24 24" className="ig-post__icon">
                <line x1="22" y1="3" x2="9.2" y2="10.1" />
                <polygon points="11.7 20.3 22 3 2 3 9.2 10.1" />
              </svg>

              <svg
                viewBox="0 0 24 24"
                className="ig-post__icon ig-post__icon--last"
              >
                <path d="M5 2h14a1 1 0 0 1 1 1v18.2c0 .8-.9 1.2-1.5.7L12 17.3l-6.5 4.6c-.6.5-1.5 0-1.5-.7V3a1 1 0 0 1 1-1z" />
              </svg>
            </div>

            <div className="ig-post__caption">
              <p className="ig-post__caption-preview">
                <strong>{ACCOUNT}</strong>{' '}
                {caption ? (
                  renderCaption(caption)
                ) : (
                  <em className="ig-post__caption-empty">
                    캡션이 비어 있습니다.
                  </em>
                )}
              </p>

              <span className="ig-post__time">
                {published && record?.publishedAt
                  ? scheduleLabel(record.publishedAt)
                  : '방금 전'}
              </span>
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
              disabled={locked}
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

            {!locked && (
              <fieldset className="ig-preview__schedule">
                <legend>발행 시점</legend>

                {/* 라디오는 접근성·키보드 조작을 위해 남기고 시각적으로만 감춘다. */}
                <div className="ig-preview__segmented" role="radiogroup">
                  <label
                    className={`ig-preview__segment ${
                      mode === 'now' ? 'ig-preview__segment--on' : ''
                    }`}
                  >
                    <input
                      type="radio"
                      name="ig-publish-mode"
                      checked={mode === 'now'}
                      onChange={() => setMode('now')}
                    />
                    지금 바로 발행
                  </label>

                  <label
                    className={`ig-preview__segment ${
                      mode === 'scheduled' ? 'ig-preview__segment--on' : ''
                    }`}
                  >
                    <input
                      type="radio"
                      name="ig-publish-mode"
                      checked={mode === 'scheduled'}
                      onChange={() => setMode('scheduled')}
                    />
                    예약 발행
                  </label>
                </div>

                {mode === 'scheduled' && (
                  <div className="ig-preview__when">
                    <label className="ig-preview__field">
                      <span>날짜</span>

                      <input
                        type="date"
                        value={scheduledDate}
                        min={earliest}
                        onChange={(event) =>
                          setScheduledDate(event.target.value)
                        }
                      />
                    </label>

                    <label className="ig-preview__field">
                      <span>시간</span>

                      <input
                        type="time"
                        value={scheduledTime}
                        onChange={(event) =>
                          setScheduledTime(event.target.value)
                        }
                      />
                    </label>

                    <p className="ig-preview__hint">
                      지금부터 {MIN_LEAD_MINUTES}분 이후 시각만 예약할 수
                      있습니다 (한국 시간).
                    </p>
                  </div>
                )}
              </fieldset>
            )}

            {published ? (
              <div className="ig-preview__status ig-preview__status--success">
                <strong>발행이 완료되었습니다.</strong>

                {record?.permalink && (
                  <a href={record.permalink} target="_blank" rel="noreferrer">
                    인스타그램에서 보기 →
                  </a>
                )}
              </div>
            ) : scheduled ? (
              <div className="ig-preview__status ig-preview__status--scheduled">
                <strong>예약된 발행입니다.</strong>

                <span>
                  {record?.scheduledAt
                    ? `${scheduleLabel(record.scheduledAt)}에 발행됩니다.`
                    : '예약 시각을 확인할 수 없습니다.'}
                </span>
              </div>
            ) : running ? (
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
              {scheduled ? (
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => void cancelSchedule()}
                  disabled={isCanceling}
                >
                  {isCanceling ? '취소 중...' : '예약 취소'}
                </button>
              ) : (
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => void save()}
                  disabled={!isDirty || isSaving || locked}
                >
                  {isSaving ? '저장 중...' : isDirty ? '캡션 저장' : '저장됨'}
                </button>
              )}

              <button
                type="button"
                className="primary-button"
                onClick={() => void publish()}
                disabled={
                  isPublishing ||
                  locked ||
                  tooLong ||
                  tooManyHashtags ||
                  !images.length ||
                  (mode === 'scheduled' && !scheduledAt)
                }
              >
                {published
                  ? '발행 완료'
                  : scheduled
                    ? '예약됨'
                    : running
                      ? '발행 중...'
                      : isPublishing
                        ? '요청 중...'
                        : mode === 'scheduled'
                          ? '예약 발행'
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
