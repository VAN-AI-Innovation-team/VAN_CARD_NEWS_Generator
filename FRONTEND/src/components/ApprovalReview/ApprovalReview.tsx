import { useEffect, useState } from 'react';
import {
  approveApprovalRequest,
  downloadApprovedCards,
  fetchApprovalRequest,
  rejectApprovalRequest,
  type ApprovalRequestListItem,
} from '../../api/approvalApi';
import {
  fetchContentPreview,
  fetchGeneratedCardImages,
  type ContentPreviewResponse,
  type GeneratedCardImageResponse,
} from '../../api/contentApi';
import './ApprovalReview.css';

interface ApprovalReviewProps {
  request: ApprovalRequestListItem;
  onCompleted: () => void;
  onBack: () => void;
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function ApprovalReview({
  request,
  onCompleted,
  onBack,
}: ApprovalReviewProps) {
  const [approval, setApproval] = useState(request);
  const [preview, setPreview] = useState<ContentPreviewResponse | null>(null);
  const [images, setImages] = useState<GeneratedCardImageResponse[]>([]);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [rejectReason, setRejectReason] = useState('');
  const [isRejectOpen, setIsRejectOpen] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        setError(null);
        const [approvalData, previewData, imageData] = await Promise.all([
          fetchApprovalRequest(request.approvalRequestId),
          fetchContentPreview(request.contentId),
          fetchGeneratedCardImages(request.contentId),
        ]);

        if (cancelled) return;

        setApproval({
          ...request,
          status: approvalData.status,
          reason: approvalData.reason,
          processedAt: approvalData.processedAt,
        });
        setPreview(previewData);
        setImages(imageData);
      } catch (loadError) {
        console.error('검수 화면 조회 실패:', loadError);
        if (!cancelled) {
          setError(
            loadError instanceof Error
              ? loadError.message
              : '검수 화면을 불러오지 못했습니다.',
          );
        }
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [request]);

  async function handleApprove() {
    if (isProcessing || approval.status !== 'PENDING') return;

    try {
      setIsProcessing(true);
      setError(null);
      const result = await approveApprovalRequest(approval.approvalRequestId);
      setApproval((current) => ({
        ...current,
        status: result.status,
        reason: result.reason,
        processedAt: result.processedAt,
      }));
      onCompleted();
    } catch (processError) {
      setError(
        processError instanceof Error
          ? processError.message
          : '승인 처리에 실패했습니다.',
      );
    } finally {
      setIsProcessing(false);
    }
  }

  async function handleReject() {
    if (isProcessing || approval.status !== 'PENDING' || !rejectReason.trim()) {
      return;
    }

    try {
      setIsProcessing(true);
      setError(null);
      const result = await rejectApprovalRequest(
        approval.approvalRequestId,
        rejectReason.trim(),
      );
      setApproval((current) => ({
        ...current,
        status: result.status,
        reason: result.reason,
        processedAt: result.processedAt,
      }));
      setIsRejectOpen(false);
      onCompleted();
    } catch (processError) {
      setError(
        processError instanceof Error
          ? processError.message
          : '반려 처리에 실패했습니다.',
      );
    } finally {
      setIsProcessing(false);
    }
  }

  async function handleDownload() {
    if (isDownloading || approval.status !== 'APPROVED') return;

    try {
      setIsDownloading(true);
      setError(null);
      const blob = await downloadApprovedCards(approval.contentId);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `content-${approval.contentId}-cards.zip`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (downloadError) {
      setError(
        downloadError instanceof Error
          ? downloadError.message
          : '최종 결과물 다운로드에 실패했습니다.',
      );
    } finally {
      setIsDownloading(false);
    }
  }

  const currentImage = images[selectedIndex];

  return (
    <div className="approval-review">
      <div className="approval-review__topbar">
        <button
          type="button"
          className="approval-review__back"
          onClick={onBack}
        >
          ← 승인 목록
        </button>
        <span
          className={`approval-review__status approval-review__status--${approval.status.toLowerCase()}`}
        >
          {approval.status === 'PENDING'
            ? '승인 대기'
            : approval.status === 'APPROVED'
              ? '승인 완료'
              : '반려됨'}
        </span>
      </div>

      {error && (
        <div className="approval-review__error" role="alert">
          {error}
        </div>
      )}

      <header className="approval-review__header">
        <div>
          <p className="approval-review__eyebrow">FINAL REVIEW · APPROVAL</p>
          <h2>{approval.title}</h2>
          <p>승인 요청일 {formatDate(approval.requestedAt)}</p>
        </div>
        <div className="approval-review__count">
          <span>전체 카드</span>
          <strong>{images.length}장</strong>
        </div>
      </header>

      <section className="approval-review__workspace">
        <aside className="approval-review__thumbs" aria-label="카드 목록">
          {images.map((image, index) => (
            <button
              type="button"
              key={image.id}
              className={`approval-review__thumb ${
                selectedIndex === index
                  ? 'approval-review__thumb--selected'
                  : ''
              }`}
              onClick={() => setSelectedIndex(index)}
            >
              <span>카드 {String(index + 1).padStart(2, '0')}</span>
              <img src={image.imageUrl} alt="" />
            </button>
          ))}
        </aside>

        <div className="approval-review__viewer">
          {currentImage ? (
            <img
              src={currentImage.imageUrl}
              alt={`${approval.title} 카드 ${selectedIndex + 1}`}
            />
          ) : (
            <div className="approval-review__empty">
              생성된 카드 이미지가 없습니다.
            </div>
          )}
          {currentImage && (
            <div className="approval-review__viewer-caption">
              CARD {String(selectedIndex + 1).padStart(2, '0')} ·{' '}
              {currentImage.cardType}
            </div>
          )}
        </div>

        <aside className="approval-review__info">
          <div>
            <span>콘텐츠</span>
            <strong>{preview?.title ?? approval.title}</strong>
          </div>
          <div>
            <span>카드 구성</span>
            <strong>{images.length}장</strong>
          </div>
          {preview?.template && (
            <div>
              <span>사용 템플릿</span>
              <strong>
                {preview.template.code} · {preview.template.name}
              </strong>
            </div>
          )}
          {approval.status === 'REJECTED' && approval.reason && (
            <div className="approval-review__reason">
              <span>반려 사유</span>
              <p>{approval.reason}</p>
            </div>
          )}
        </aside>
      </section>

      {isRejectOpen && approval.status === 'PENDING' && (
        <section className="approval-review__reject-form">
          <label htmlFor="reject-reason">반려 사유</label>
          <textarea
            id="reject-reason"
            value={rejectReason}
            onChange={(event) => setRejectReason(event.target.value)}
            placeholder="수정이 필요한 내용을 입력해주세요."
            maxLength={1000}
          />
          <div className="approval-review__reject-actions">
            <span>{rejectReason.length}/1000</span>
            <button
              type="button"
              className="secondary-button"
              onClick={() => setIsRejectOpen(false)}
              disabled={isProcessing}
            >
              취소
            </button>
            <button
              type="button"
              className="approval-review__danger-button"
              onClick={() => void handleReject()}
              disabled={isProcessing || !rejectReason.trim()}
            >
              {isProcessing ? '처리 중...' : '반려 확정'}
            </button>
          </div>
        </section>
      )}

      <footer className="approval-review__actions">
        {approval.status === 'PENDING' ? (
          <>
            <button
              type="button"
              className="approval-review__danger-button"
              onClick={() => setIsRejectOpen(true)}
              disabled={isProcessing}
            >
              반려
            </button>
            <button
              type="button"
              className="primary-button"
              onClick={() => void handleApprove()}
              disabled={isProcessing || images.length === 0}
            >
              {isProcessing ? '처리 중...' : '승인'}
            </button>
          </>
        ) : approval.status === 'APPROVED' ? (
          <button
            type="button"
            className="primary-button"
            onClick={() => void handleDownload()}
            disabled={isDownloading}
          >
            {isDownloading ? '다운로드 중...' : '최종 결과물 다운로드'}
          </button>
        ) : (
          <button type="button" className="secondary-button" onClick={onBack}>
            승인 대기 목록으로
          </button>
        )}
      </footer>
    </div>
  );
}
