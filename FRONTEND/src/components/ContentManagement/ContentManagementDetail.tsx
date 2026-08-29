import { useEffect, useState } from 'react';
import {
  downloadApprovedCards,
  fetchApprovalRequest,
  type ApprovalRequestResponse,
} from '../../api/approvalApi';
import {
  fetchContentPreview,
  fetchGeneratedCardImages,
  requestContentApproval,
  type ContentManagementListItem,
  type ContentPreviewResponse,
  type GeneratedCardImageResponse,
} from '../../api/contentApi';
import './ContentManagementDetail.css';

type ApprovalState = ApprovalRequestResponse & {
  reason: string | null;
  processedAt: string | null;
};

interface Props {
  content: ContentManagementListItem;
  onBack: () => void;
  onUpdated: () => void;
  onEdit: (preview: ContentPreviewResponse) => void;
  onCloneAndRegenerate: (contentId: number) => Promise<void>;
}

function label(
  status: ApprovalState['status'] | null,
  generationStatus: ContentManagementListItem['generationStatus'],
) {
  if (generationStatus === 'PENDING' || generationStatus === 'PROCESSING')
    return '생성 중';
  if (generationStatus === 'FAILED') return '생성 실패';
  if (generationStatus === 'IMAGE_PENDING') return '이미지 생성 대기';
  if (status === 'PENDING') return '승인 대기';
  if (status === 'APPROVED') return '승인 완료';
  if (status === 'REJECTED') return '반려됨';
  return '승인 요청 전';
}

export default function ContentManagementDetail({
  content,
  onBack,
  onUpdated,
  onEdit,
  onCloneAndRegenerate,
}: Props) {
  const [preview, setPreview] = useState<ContentPreviewResponse | null>(null);
  const [images, setImages] = useState<GeneratedCardImageResponse[]>([]);
  const [approval, setApproval] = useState<ApprovalState | null>(null);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [isProcessing, setIsProcessing] = useState(false);
  const [isCloning, setIsCloning] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const generationInProgress =
    preview?.generationStatus === 'PENDING' ||
    preview?.generationStatus === 'PROCESSING';

  async function load(silent = false) {
    try {
      if (!silent) setIsLoading(true);
      setError(null);
      const [previewData, imageData] = await Promise.all([
        fetchContentPreview(content.contentId),
        fetchGeneratedCardImages(content.contentId),
      ]);
      const approvalData =
        content.approvalRequestId === null
          ? null
          : await fetchApprovalRequest(content.approvalRequestId);
      setPreview(previewData);
      setImages(imageData);
      setApproval(approvalData);
      setSelectedIndex(0);
    } catch (loadError) {
      setError(
        loadError instanceof Error
          ? loadError.message
          : '콘텐츠 상세 정보를 불러오지 못했습니다.',
      );
    } finally {
      if (!silent) setIsLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [content.contentId, content.approvalRequestId]);

  useEffect(() => {
    if (!generationInProgress) return;

    const timer = window.setInterval(() => {
      void load(true);
    }, 1000);

    return () => window.clearInterval(timer);
  }, [generationInProgress]);

  async function requestApproval() {
    if (
      isProcessing ||
      !images.length ||
      approval?.status === 'PENDING' ||
      approval?.status === 'APPROVED'
    )
      return;
    try {
      setIsProcessing(true);
      setError(null);
      const result = await requestContentApproval(content.contentId);
      setApproval({ ...result, reason: null, processedAt: null });
      onUpdated();
    } catch (e) {
      setError(e instanceof Error ? e.message : '승인 요청에 실패했습니다.');
    } finally {
      setIsProcessing(false);
    }
  }

  function handleEdit() {
    if (!preview || isProcessing || isCloning) return;
    onEdit(preview);
  }

  async function handleCloneAndRegenerate() {
    if (isCloning || isProcessing) return;

    try {
      setIsCloning(true);
      setError(null);
      await onCloneAndRegenerate(content.contentId);
    } catch (cloneError) {
      setError(
        cloneError instanceof Error
          ? cloneError.message
          : '콘텐츠 복제 및 재생성에 실패했습니다.',
      );
    } finally {
      setIsCloning(false);
    }
  }

  async function download() {
    if (isDownloading || approval?.status !== 'APPROVED') return;
    try {
      setIsDownloading(true);
      setError(null);
      const blob = await downloadApprovedCards(content.contentId);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `content-${content.contentId}-cards.zip`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : '최종 결과물 다운로드에 실패했습니다.',
      );
    } finally {
      setIsDownloading(false);
    }
  }

  const currentImage = images[selectedIndex];
  const status =
    approval?.status ??
    preview?.approvalStatus ??
    content.approvalStatus ??
    null;

  return (
    <section className="content-management-detail">
      <div className="content-management-detail__topbar">
        <button
          type="button"
          className="content-management-detail__back"
          onClick={onBack}
        >
          ← 콘텐츠 관리
        </button>
        <span
          className={`content-management-detail__status content-management-detail__status--${(status ?? 'none').toLowerCase()}`}
        >
          {label(status, preview?.generationStatus ?? content.generationStatus)}
        </span>
      </div>
      {error && (
        <div className="content-management-detail__error" role="alert">
          {error}
        </div>
      )}

      <header className="content-management-detail__header">
        <div>
          <p className="content-management-detail__eyebrow">
            CONTENT · DETAIL & REVIEW
          </p>
          <h2>{preview?.title ?? content.title}</h2>
          <p>콘텐츠 #{content.contentId}</p>
        </div>
        <div className="content-management-detail__meta">
          <span>카드 수</span>
          <strong>{isLoading ? '-' : `${images.length}장`}</strong>
        </div>
      </header>

      {isLoading ? (
        <div className="content-management-detail__state">
          <div className="content-management-detail__spinner" />
          <strong>상세 정보를 불러오는 중입니다.</strong>
        </div>
      ) : (
        <>
          <section className="content-management-detail__workspace">
            <aside
              className="content-management-detail__thumbs"
              aria-label="카드 목록"
            >
              {images.map((image, index) => (
                <button
                  type="button"
                  key={image.id}
                  className={`content-management-detail__thumb ${selectedIndex === index ? 'content-management-detail__thumb--selected' : ''}`}
                  onClick={() => setSelectedIndex(index)}
                >
                  <span>카드 {String(index + 1).padStart(2, '0')}</span>
                  <img src={image.imageUrl} alt="" />
                </button>
              ))}
            </aside>
            <div className="content-management-detail__viewer">
              {currentImage ? (
                <img
                  src={currentImage.imageUrl}
                  alt={`${content.title} 카드 ${selectedIndex + 1}`}
                />
              ) : (
                <div className="content-management-detail__empty-card">
                  <strong>생성된 카드 이미지가 없습니다.</strong>
                  <span>최종 이미지 생성이 완료되지 않았습니다.</span>
                </div>
              )}
              {currentImage && (
                <div className="content-management-detail__caption">
                  CARD {String(selectedIndex + 1).padStart(2, '0')} ·{' '}
                  {currentImage.cardType}
                </div>
              )}
            </div>
            <aside className="content-management-detail__info">
              <div>
                <span>승인 상태</span>
                <strong>
                  {label(
                    status,
                    preview?.generationStatus ?? content.generationStatus,
                  )}
                </strong>
              </div>
              {preview?.template && (
                <div>
                  <span>사용 템플릿</span>
                  <strong>
                    {preview.template.code} · {preview.template.name}
                  </strong>
                </div>
              )}
              {approval?.status === 'REJECTED' && approval.reason && (
                <div className="content-management-detail__reason">
                  <span>반려 사유</span>
                  <p>{approval.reason}</p>
                </div>
              )}
            </aside>
          </section>

          <footer className="content-management-detail__actions">
            <button
              type="button"
              className="secondary-button content-management-detail__clone-button"
              onClick={() => void handleCloneAndRegenerate()}
              disabled={isProcessing || isCloning}
            >
              {isCloning ? '복제 및 재생성 중...' : '복제 · 재생성'}
            </button>

            {generationInProgress ? (
              <>
                <button
                  type="button"
                  className="secondary-button"
                  onClick={handleEdit}
                  disabled={!preview || isProcessing || isCloning}
                >
                  콘텐츠 이어서 작업
                </button>
                <button type="button" className="primary-button" disabled>
                  생성 진행 중
                </button>
              </>
            ) : status === 'APPROVED' ? (
              <button
                type="button"
                className="primary-button"
                onClick={() => void download()}
                disabled={isDownloading}
              >
                {isDownloading ? '다운로드 중...' : '최종 결과물 다운로드'}
              </button>
            ) : status === 'PENDING' ? (
              <button type="button" className="primary-button" disabled>
                승인 요청 대기 중
              </button>
            ) : status === 'REJECTED' ? (
              <>
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => preview && onEdit(preview)}
                  disabled={!preview || isProcessing}
                >
                  콘텐츠 수정
                </button>
                <button
                  type="button"
                  className="primary-button"
                  onClick={() => void requestApproval()}
                  disabled={isProcessing || !images.length}
                >
                  {isProcessing ? '승인 요청 중...' : '재승인 요청'}
                </button>
              </>
            ) : content.contentStatus === 'DRAFT' ? (
              <>
                <button
                  type="button"
                  className="secondary-button"
                  onClick={handleEdit}
                  disabled={!preview || isProcessing || isCloning}
                >
                  콘텐츠 수정
                </button>
                <button
                  type="button"
                  className="primary-button"
                  onClick={() => void requestApproval()}
                  disabled={isProcessing || !images.length}
                >
                  {isProcessing ? '승인 요청 중...' : '승인 요청'}
                </button>
              </>
            ) : (
              <button type="button" className="secondary-button" disabled>
                승인 요청 불가
              </button>
            )}
          </footer>
        </>
      )}
    </section>
  );
}
