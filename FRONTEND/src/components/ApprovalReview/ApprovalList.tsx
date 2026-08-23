import { useEffect, useState } from 'react';
import {
  fetchPendingApprovalRequests,
  type ApprovalRequestListItem,
} from '../../api/approvalApi';
import './ApprovalList.css';

interface ApprovalListProps {
  onSelect: (request: ApprovalRequestListItem) => void;
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function ApprovalList({ onSelect }: ApprovalListProps) {
  const [requests, setRequests] = useState<ApprovalRequestListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    try {
      setIsLoading(true);
      setError(null);
      setRequests(await fetchPendingApprovalRequests());
    } catch (loadError) {
      setError(
        loadError instanceof Error
          ? loadError.message
          : '승인 대기 목록을 불러오지 못했습니다.',
      );
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  return (
    <section className="approval-list">
      <div className="approval-list__header">
        <div>
          <p className="approval-list__eyebrow">REVIEW · APPROVAL</p>
          <h2>검수 · 승인</h2>
          <p>승인 요청된 카드뉴스를 확인하고 최종 승인 또는 반려합니다.</p>
        </div>
        <button
          type="button"
          className="secondary-button"
          onClick={() => void load()}
          disabled={isLoading}
        >
          {isLoading ? '조회 중...' : '새로고침'}
        </button>
      </div>

      {error && (
        <div className="approval-list__error" role="alert">
          {error}
        </div>
      )}

      {!isLoading && !error && requests.length === 0 ? (
        <div className="approval-list__empty">
          <strong>승인 대기 중인 작업물이 없습니다.</strong>
          <p>
            카드뉴스 생성 화면에서 승인 요청을 보내면 이곳에서 확인할 수
            있습니다.
          </p>
        </div>
      ) : (
        <div className="approval-list__table">
          <div className="approval-list__row approval-list__row--head">
            <span>콘텐츠</span>
            <span>요청일</span>
            <span>상태</span>
            <span />
          </div>

          {requests.map((request) => (
            <button
              type="button"
              key={request.approvalRequestId}
              className="approval-list__row approval-list__row--item"
              onClick={() => onSelect(request)}
            >
              <span className="approval-list__title">
                {request.title}
                <small>콘텐츠 #{request.contentId}</small>
              </span>
              <span>{formatDate(request.requestedAt)}</span>
              <span>
                <em>승인 대기</em>
              </span>
              <span className="approval-list__arrow">→</span>
            </button>
          ))}
        </div>
      )}
    </section>
  );
}
