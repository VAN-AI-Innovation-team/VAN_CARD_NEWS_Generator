import { useEffect, useState } from 'react';
import axios from 'axios';

import { TemplateProvider, useTemplate } from './contexts/TemplateContext';

import { TemplateSelector } from './components/TemplateSelector/TemplateSelector';

import PostInputForm, {
  type PostInputFormSubmitPayload,
} from './components/PostForm/PostInputForm';

import ContentTypeSelector from './components/ContentTypeSelector/ContentTypeSelector';

import type { ContentType } from './types/content';

import { createContent } from './api/contentApi';

import './App.css';

type Step = 1 | 2 | 3;

function App() {
  return (
    <TemplateProvider>
      <AppContent />
    </TemplateProvider>
  );
}

function AppContent() {
  const [step, setStep] = useState<Step>(1);

  const [message, setMessage] = useState('연동 확인 중...');

  const [selectedContentType, setSelectedContentType] =
    useState<ContentType | null>(null);

  const [postData, setPostData] = useState<PostInputFormSubmitPayload | null>(
    null,
  );

  const [isCreating, setIsCreating] = useState(false);

  const { selectedTemplateId, clearSelectedTemplate } = useTemplate();

  /**
   * STEP 02 콘텐츠 작성 완료 여부
   */
  const isPostDataValid =
    postData !== null &&
    postData.title.trim().length > 0 &&
    postData.body.trim().length > 0 &&
    postData.images.length > 0;

  /**
   * 백엔드 연결 상태 확인
   */
  async function checkBackendConnection() {
    try {
      const response = await axios.get('/api/health');

      setMessage(response.data.message);
    } catch (error) {
      console.error('백엔드 통신 오류:', error);

      setMessage('백엔드 연결 실패');
    }
  }

  /**
   * 앱 최초 실행
   */
  useEffect(() => {
    checkBackendConnection();
  }, []);

  /**
   * 콘텐츠 유형 → STEP 02
   */
  function handleNextFromContentType() {
    if (!selectedContentType) {
      return;
    }

    setStep(2);
  }

  /**
   * STEP 02 → STEP 03
   */
  function handleNextFromContent() {
    if (!isPostDataValid) {
      return;
    }

    setStep(3);
  }

  /**
   * STEP 03 → 실제 카드뉴스 생성 요청
   */
  async function handleCreateContent() {
    if (!isPostDataValid) {
      return;
    }

    if (!selectedTemplateId) {
      return;
    }

    try {
      setIsCreating(true);

      await createContent({
        title: postData.title,
        body: postData.body,
        images: postData.images,
        templateId: selectedTemplateId,
      });

      /*
       * 현재는 미리보기 화면을 만들지 않으므로
       * 성공 후에도 STEP 03에 그대로 유지합니다.
       */
    } catch (error) {
      console.error('카드뉴스 생성 요청 실패:', error);
    } finally {
      setIsCreating(false);
    }
  }

  /**
   * 이전 단계로 이동
   */
  function handlePreviousStep() {
    if (step === 2) {
      setStep(1);
      return;
    }

    if (step === 3) {
      setStep(2);
    }
  }

  /**
   * 새 카드뉴스 제작 시작
   */
  function handleStartNewContent() {
    setStep(1);

    setSelectedContentType(null);

    setPostData(null);

    clearSelectedTemplate();
  }

  return (
    <div className="app">
      {/* ================================
          HEADER
      ================================= */}
      <header className="app-header">
        <div className="app-header__inner">
          <div className="app-brand">
            <span className="app-brand__name">VAN</span>

            <div className="app-brand__divider" />

            <div>
              <p className="app-brand__eyebrow">AI CONTENT GENERATOR</p>

              <h1 className="app-header__title">카드뉴스 제작 시스템</h1>
            </div>
          </div>

          <div className="connection-status">
            <span
              className={`connection-status__dot ${
                message === '연동 확인 중...'
                  ? 'connection-status__dot--loading'
                  : message === '백엔드 연결 실패'
                    ? 'connection-status__dot--error'
                    : 'connection-status__dot--success'
              }`}
            />

            <span className="connection-status__text">{message}</span>

            {message === '백엔드 연결 실패' && (
              <button
                type="button"
                className="connection-status__button"
                onClick={checkBackendConnection}
              >
                다시 확인
              </button>
            )}
          </div>
        </div>
      </header>

      <main className="app-main">
        {/* ================================
            PAGE INTRO
        ================================= */}
        <section className="page-intro">
          <p className="page-intro__eyebrow">CARD NEWS GENERATOR</p>

          <h2 className="page-intro__title">새 카드뉴스 제작</h2>

          <p className="page-intro__description">
            콘텐츠 정보를 입력하면 콘텐츠 유형에 맞는 템플릿을 추천하고 카드뉴스
            제작을 진행합니다.
          </p>
        </section>

        {/* ================================
            STEP NAVIGATION
        ================================= */}
        <nav className="step-navigation" aria-label="카드뉴스 제작 단계">
          {/* STEP 01 */}
          <div
            className={`step-item ${step >= 1 ? 'step-item--active' : ''} ${
              step > 1 ? 'step-item--completed' : ''
            }`}
          >
            <span className="step-item__number">01</span>

            <div className="step-item__content">
              <span className="step-item__label">콘텐츠 유형</span>

              <span className="step-item__description">제작 목적 선택</span>
            </div>
          </div>

          <span className="step-navigation__line" />

          {/* STEP 02 */}
          <div
            className={`step-item ${step >= 2 ? 'step-item--active' : ''} ${
              step > 2 ? 'step-item--completed' : ''
            }`}
          >
            <span className="step-item__number">02</span>

            <div className="step-item__content">
              <span className="step-item__label">콘텐츠 작성</span>

              <span className="step-item__description">
                제목·본문·사진 입력
              </span>
            </div>
          </div>

          <span className="step-navigation__line" />

          {/* STEP 03 */}
          <div className={`step-item ${step >= 3 ? 'step-item--active' : ''}`}>
            <span className="step-item__number">03</span>

            <div className="step-item__content">
              <span className="step-item__label">템플릿 선택</span>

              <span className="step-item__description">추천 템플릿 확인</span>
            </div>
          </div>
        </nav>

        {/* ================================
            WORKFLOW
        ================================= */}
        <section className="workflow-panel">
          {/* STEP 01 */}
          {step === 1 && (
            <div className="workflow-step">
              <div className="workflow-step__header">
                <p className="workflow-step__eyebrow">STEP 01 · CONTENT TYPE</p>

                <h3 className="workflow-step__title">
                  콘텐츠 유형을 선택해주세요.
                </h3>

                <p className="workflow-step__description">
                  선택한 콘텐츠 유형을 기준으로 이후 단계에서 적합한 카드뉴스
                  템플릿을 추천합니다.
                </p>
              </div>

              <ContentTypeSelector
                selectedType={selectedContentType}
                onSelect={setSelectedContentType}
              />

              <div className="workflow-navigation">
                <div />

                <button
                  type="button"
                  className="primary-button"
                  disabled={!selectedContentType}
                  onClick={handleNextFromContentType}
                >
                  다음
                  <span aria-hidden="true">→</span>
                </button>
              </div>
            </div>
          )}

          {/* STEP 02 */}
          {step === 2 && (
            <div className="workflow-step">
              <div className="workflow-step__header">
                <p className="workflow-step__eyebrow">STEP 02 · CONTENT</p>

                <h3 className="workflow-step__title">
                  카드뉴스에 사용할 내용을 입력해주세요.
                </h3>

                <p className="workflow-step__description">
                  제목, 본문, 사진을 입력한 후 다음 단계에서 사용할 템플릿을
                  선택합니다.
                </p>
              </div>

              <PostInputForm initialData={postData} onChange={setPostData} />

              <div className="workflow-navigation workflow-navigation--form">
                <button
                  type="button"
                  className="secondary-button"
                  onClick={handlePreviousStep}
                >
                  <span aria-hidden="true">←</span>
                  이전
                </button>

                <button
                  type="button"
                  className="primary-button"
                  disabled={!isPostDataValid}
                  onClick={handleNextFromContent}
                >
                  다음
                  <span aria-hidden="true">→</span>
                </button>
              </div>
            </div>
          )}

          {/* STEP 03 */}
          {step === 3 && (
            <div className="workflow-step">
              <div className="workflow-step__header">
                <p className="workflow-step__eyebrow">STEP 03 · TEMPLATE</p>

                <h3 className="workflow-step__title">
                  추천 템플릿을 선택해주세요.
                </h3>

                <p className="workflow-step__description">
                  선택한 콘텐츠 유형에 적합한 템플릿을 확인하고 카드뉴스 제작에
                  사용할 디자인을 선택합니다.
                </p>
              </div>

              <TemplateSelector contentType={selectedContentType} />

              <div className="workflow-navigation">
                <button
                  type="button"
                  className="secondary-button"
                  disabled={isCreating}
                  onClick={handlePreviousStep}
                >
                  <span aria-hidden="true">←</span>
                  이전
                </button>

                <button
                  type="button"
                  className="primary-button"
                  disabled={
                    !isPostDataValid || !selectedTemplateId || isCreating
                  }
                  onClick={handleCreateContent}
                >
                  {isCreating ? '생성 요청 중...' : '카드뉴스 만들기'}
                </button>
              </div>
            </div>
          )}
        </section>
      </main>

      <footer className="app-footer">
        <span>VAN Card News Generator</span>

        <span>AI 콘텐츠 자동 제작 시스템</span>
      </footer>
    </div>
  );
}

export default App;
