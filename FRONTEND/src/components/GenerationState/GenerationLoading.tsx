function GenerationLoading() {
  return (
    <div
      className="creation-state creation-state--loading"
      role="status"
      aria-live="polite"
    >
      <div className="creation-state__spinner" aria-hidden="true" />
      <p className="creation-state__eyebrow">GENERATING</p>
      <h3 className="creation-state__title">카드뉴스를 생성하고 있어요.</h3>
      <p className="creation-state__description">
        입력하신 내용을 바탕으로 카드 구성을 만드는 중입니다.
        <br />
        완료되면 자동으로 결과 화면으로 이동합니다.
      </p>
    </div>
  );
}

export default GenerationLoading;
