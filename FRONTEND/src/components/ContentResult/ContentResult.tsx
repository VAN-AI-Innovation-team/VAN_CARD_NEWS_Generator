import { useEffect, useState } from 'react';

import {
  requestContentApproval,
  type ContentPreviewResponse,
  type GeneratedCardImageResponse,
} from '../../api/contentApi';

import './ContentResult.css';

interface ContentResultProps {
  preview: ContentPreviewResponse;
  images: GeneratedCardImageResponse[];
  isLoading: boolean;
  error: string | null;
}

export default function ContentResult({
  preview,
  images,
  isLoading,
  error,
}: ContentResultProps) {
  const [approvalStatus, setApprovalStatus] = useState(preview.approvalStatus);
  const [isRequestingApproval, setIsRequestingApproval] = useState(false);
  const [approvalError, setApprovalError] = useState<string | null>(null);

  useEffect(() => {
    setApprovalStatus(preview.approvalStatus);
  }, [preview.approvalStatus]);

  async function handleRequestApproval() {
    if (
      isRequestingApproval ||
      approvalStatus === 'PENDING' ||
      approvalStatus === 'APPROVED' ||
      images.length === 0
    ) {
      return;
    }

    try {
      setIsRequestingApproval(true);
      setApprovalError(null);

      const response = await requestContentApproval(preview.contentId);

      setApprovalStatus(response.status);
    } catch (requestError) {
      console.error('승인 요청 실패:', requestError);

      setApprovalError(
        requestError instanceof Error
          ? requestError.message
          : '승인 요청에 실패했습니다.',
      );
    } finally {
      setIsRequestingApproval(false);
    }
  }

  return (
    <div className="content-result">
      <div className="content-result__summary">
        <div>
          <span className="content-result__eyebrow">GENERATION COMPLETE</span>

          <h4 className="content-result__title">{preview.title}</h4>

          <p className="content-result__description">
            Higgsfield에서 생성된 최종 카드 이미지를 순서대로 확인할 수
            있습니다.
          </p>
        </div>

        <div className="content-result__meta">
          <span>카드 수</span>
          <strong>{isLoading ? '-' : `${images.length}장`}</strong>
        </div>
      </div>

      {isLoading ? (
        <div className="content-result__state">
          <div className="content-result__spinner" aria-hidden="true" />

          <strong>카드뉴스를 생성하고 있습니다.</strong>

          <p>
            Higgsfield에서 최종 카드 이미지를 생성하는 중입니다. 잠시만
            기다려주세요.
          </p>
        </div>
      ) : error ? (
        <div
          className="content-result__state content-result__state--error"
          role="alert"
        >
          <strong>카드뉴스 생성에 실패했습니다.</strong>

          <p>{error}</p>
        </div>
      ) : images.length === 0 ? (
        <div className="content-result__state">
          <strong>생성된 결과가 없습니다.</strong>

          <p>카드뉴스 생성 결과를 확인할 수 없습니다.</p>
        </div>
      ) : (
        <>
          <div className="content-result__cards">
            {images.map((image, index) => (
              <article key={image.id} className="content-result__card">
                <div className="content-result__card-header">
                  <span>CARD {String(index + 1).padStart(2, '0')}</span>

                  <small>{image.cardType}</small>
                </div>

                <img
                  src={image.imageUrl}
                  alt={`${preview.title} 카드 ${index + 1}`}
                  className="content-result__image"
                />
              </article>
            ))}
          </div>

          <div className="content-result__approval">
            <div>
              <span className="content-result__approval-label">승인 상태</span>

              <strong>
                {approvalStatus === 'PENDING'
                  ? '승인 대기'
                  : approvalStatus === 'APPROVED'
                    ? '승인 완료'
                    : approvalStatus === 'REJECTED'
                      ? '반려됨'
                      : '승인 요청 전'}
              </strong>
            </div>

            <button
              type="button"
              className="content-result__approval-button"
              disabled={
                isRequestingApproval ||
                approvalStatus === 'PENDING' ||
                approvalStatus === 'APPROVED'
              }
              onClick={() => void handleRequestApproval()}
            >
              {isRequestingApproval
                ? '승인 요청 중...'
                : approvalStatus === 'PENDING'
                  ? '승인 대기 중'
                  : approvalStatus === 'APPROVED'
                    ? '승인 완료'
                    : '승인 요청'}
            </button>
          </div>

          {approvalError && (
            <p className="content-result__error" role="alert">
              {approvalError}
            </p>
          )}
        </>
      )}
    </div>
  );
}
