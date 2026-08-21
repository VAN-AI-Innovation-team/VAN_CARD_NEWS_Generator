interface GenerationErrorProps {
  message: string;
  onRetry: () => void;
  onEditTemplate: () => void;
  retryDisabled?: boolean;
}

function GenerationError({
  message,
  onRetry,
  onEditTemplate,
  retryDisabled = false,
}: GenerationErrorProps) {
  return (
    <div className="creation-state creation-state--error" role="alert">
      <p className="creation-state__eyebrow">GENERATION FAILED</p>
      <h3 className="creation-state__title">카드뉴스 생성에 실패했습니다.</h3>
      <p className="creation-state__description">{message}</p>

      <div className="creation-state__actions">
        <button
          type="button"
          className="secondary-button"
          onClick={onEditTemplate}
        >
          <span aria-hidden="true">←</span>
          템플릿 수정
        </button>

        <button
          type="button"
          className="primary-button"
          disabled={retryDisabled}
          onClick={onRetry}
        >
          다시 생성
        </button>
      </div>
    </div>
  );
}

export default GenerationError;
