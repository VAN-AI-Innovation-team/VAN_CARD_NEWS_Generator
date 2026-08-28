import { useEffect, useState } from 'react';
import {
  fetchContentManagementList,
  type ContentManagementListItem,
} from '../../api/contentApi';
import './ContentManagement.css';

interface ContentManagementProps {
  onSelect: (content: ContentManagementListItem) => void;
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
}

function approvalLabel(
  status: ContentManagementListItem['approvalStatus'],
  generationStatus: ContentManagementListItem['generationStatus'],
) {
  if (generationStatus === 'PENDING' || generationStatus === 'PROCESSING') {
    return '생성 중';
  }
  if (generationStatus === 'FAILED') return '생성 실패';
  if (generationStatus === 'IMAGE_PENDING') return '이미지 생성 대기';
  if (status === 'PENDING') return '승인 대기';
  if (status === 'APPROVED') return '승인 완료';
  if (status === 'REJECTED') return '반려됨';
  return '승인 요청 전';
}

export default function ContentManagement({
  onSelect,
}: ContentManagementProps) {
  const [contents, setContents] = useState<ContentManagementListItem[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const PAGE_SIZE = 20;
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load(targetPage = page) {
    try {
      setIsLoading(true);
      setError(null);
      const result = await fetchContentManagementList(targetPage, PAGE_SIZE);
      setContents(result.contents);
      setPage(result.page);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    } catch (loadError) {
      console.error('콘텐츠 관리 목록 조회 실패:', loadError);
      setError(
        loadError instanceof Error
          ? loadError.message
          : '콘텐츠 목록을 불러오지 못했습니다.',
      );
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void load(page);
  }, [page]);

  return (
    <section className="content-management">
      <div className="content-management__header">
        <div>
          <p className="content-management__eyebrow">CONTENT · MANAGEMENT</p>
          <h2>콘텐츠 관리</h2>
          <p>
            생성된 카드뉴스를 확인하고 승인 상태와 최종 작업물을 관리합니다.
          </p>
        </div>
        <button
          type="button"
          className="secondary-button"
          onClick={() => void load(page)}
          disabled={isLoading}
        >
          {isLoading ? '조회 중...' : '새로고침'}
        </button>
      </div>

      {error && (
        <div className="content-management__error" role="alert">
          <strong>목록을 불러오지 못했습니다.</strong>
          <span>{error}</span>
          <button type="button" onClick={() => void load()}>
            다시 시도
          </button>
        </div>
      )}

      {!isLoading && !error && contents.length === 0 ? (
        <div className="content-management__empty">
          <div className="content-management__empty-icon" aria-hidden="true">
            ＋
          </div>
          <strong>생성된 콘텐츠가 없습니다.</strong>
          <p>
            카드뉴스를 생성하면 이곳에서 콘텐츠별 결과와 승인 상태를 확인할 수
            있습니다.
          </p>
        </div>
      ) : (
        <div className="content-management__table" aria-busy={isLoading}>
          <div className="content-management__row content-management__row--head">
            <span>콘텐츠</span>
            <span>생성일</span>
            <span>카드 수</span>
            <span>승인 상태</span>
            <span />
          </div>
          {contents.map((content) => (
            <button
              type="button"
              key={content.contentId}
              className="content-management__row content-management__row--item"
              onClick={() => onSelect(content)}
            >
              <span className="content-management__title">
                <strong>{content.title}</strong>
                <small>콘텐츠 #{content.contentId}</small>
              </span>
              <span>{formatDate(content.createdAt)}</span>
              <span>
                {content.generationStatus === 'PENDING' ||
                content.generationStatus === 'PROCESSING'
                  ? '생성 중'
                  : content.generationStatus === 'IMAGE_PENDING'
                    ? '이미지 생성 대기'
                    : content.generationStatus === 'FAILED'
                      ? '생성 실패'
                      : `${content.cardCount}장`}
              </span>
              <span>
                <em
                  className={`content-management__status content-management__status--${(content.approvalStatus ?? 'none').toLowerCase()}`}
                >
                  {approvalLabel(
                    content.approvalStatus,
                    content.generationStatus,
                  )}
                </em>
              </span>
              <span className="content-management__arrow">→</span>
            </button>
          ))}
        </div>
      )}

      {!isLoading && !error && totalPages > 1 && (
        <nav
          className="content-management__pagination"
          aria-label="콘텐츠 페이지 이동"
        >
          <button
            type="button"
            className="content-management__page-button content-management__page-button--arrow"
            onClick={() => setPage((current) => Math.max(0, current - 1))}
            disabled={page === 0}
            aria-label="이전 페이지"
          >
            ←
          </button>

          {Array.from({ length: totalPages }, (_, index) => index).map(
            (pageNumber) => (
              <button
                type="button"
                key={pageNumber}
                className={`content-management__page-button ${pageNumber === page ? 'content-management__page-button--active' : ''}`}
                onClick={() => setPage(pageNumber)}
                aria-current={pageNumber === page ? 'page' : undefined}
              >
                {pageNumber + 1}
              </button>
            ),
          )}

          <button
            type="button"
            className="content-management__page-button content-management__page-button--arrow"
            onClick={() =>
              setPage((current) => Math.min(totalPages - 1, current + 1))
            }
            disabled={page >= totalPages - 1}
            aria-label="다음 페이지"
          >
            →
          </button>

          <span className="content-management__pagination-count">
            전체 {totalElements}개
          </span>
        </nav>
      )}
    </section>
  );
}
