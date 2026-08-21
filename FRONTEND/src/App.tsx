import { useEffect, useState } from 'react';
import axios from 'axios';

import { TemplateProvider, useTemplate } from './contexts/TemplateContext';

import { TemplateSelector } from './components/TemplateSelector/TemplateSelector';
import CardNewsEditor from './components/CardNewsPreview/CardNewsEditor';
import PostInputForm, {
  type PostInputFormSubmitPayload,
} from './components/PostForm/PostInputForm';
import GenerationLoading from './components/GenerationState/GenerationLoading';
import GenerationError from './components/GenerationState/GenerationError';

import ContentTypeSelector from './components/ContentTypeSelector/ContentTypeSelector';

import type { ContentType } from './types/content';

import {
  createContent,
  fetchContentPreview,
  type ContentPreviewResponse,
} from './api/contentApi';

import './App.css';

type Step = 1 | 2 | 3 | 4;

const PREVIEW_POLL_INTERVAL = 1000;
const PREVIEW_POLL_MAX_COUNT = 30;

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

  const [preview, setPreview] = useState<ContentPreviewResponse | null>(null);

  const [previewError, setPreviewError] = useState<string | null>(null);

  const { selectedTemplateId, clearSelectedTemplate } = useTemplate();

  const isPostDataValid =
    postData !== null &&
    postData.title.trim().length > 0 &&
    postData.body.trim().length > 0 &&
    postData.images.length > 0;

  async function checkBackendConnection() {
    try {
      const response = await axios.get('/api/health');

      setMessage(response.data.message);
    } catch (error) {
      console.error('백엔드 통신 오류:', error);
      setMessage('백엔드 연결 실패');
    }
  }

  useEffect(() => {
    void checkBackendConnection();
  }, []);

  function handleNextFromContentType() {
    if (!selectedContentType) {
      return;
    }

    setStep(2);
  }

  function handleNextFromContent() {
    if (!isPostDataValid) {
      return;
    }

    setStep(3);
  }

  async function waitForPreview(contentId: number) {
    for (let attempt = 0; attempt < PREVIEW_POLL_MAX_COUNT; attempt += 1) {
      const result = await fetchContentPreview(contentId);

      if (result.cardGenerationResult !== null) {
        return result;
      }

      await new Promise((resolve) =>
        setTimeout(resolve, PREVIEW_POLL_INTERVAL),
      );
    }

    throw new Error(
      '카드 구성 결과 생성 시간이 초과되었습니다. 다시 시도해주세요.',
    );
  }

  async function handleCreateContent() {
    if (!isPostDataValid || !selectedTemplateId) {
      return;
    }

    try {
      setIsCreating(true);
      setPreviewError(null);
      setPreview(null);

      const response = await createContent({
        title: postData.title,
        body: postData.body,
        images: postData.images,
        templateId: selectedTemplateId,
      });

      const previewData = await waitForPreview(response.contentId);

      setPreview(previewData);
      setStep(4);
    } catch (error) {
      console.error('카드뉴스 생성/미리보기 실패:', error);

      setPreviewError(
        error instanceof Error
          ? error.message
          : '카드뉴스 생성 중 오류가 발생했습니다.',
      );
    } finally {
      setIsCreating(false);
    }
  }

  function handlePreviousStep() {
    if (step === 2) {
      setStep(1);
      return;
    }

    if (step === 3) {
      setStep(2);
      return;
    }

    if (step === 4) {
      setStep(3);
    }
  }

  function handleEditTemplate() {
    setPreviewError(null);
    setStep(1);
  }

  function handleStartNewContent() {
    setStep(1);
    setSelectedContentType(null);
    setPostData(null);
    setPreview(null);
    setPreviewError(null);
    clearSelectedTemplate();
  }

  return (
    <div className="app">
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
        <section className="page-intro">
          <p className="page-intro__eyebrow">CARD NEWS GENERATOR</p>

          <h2 className="page-intro__title">새 카드뉴스 제작</h2>

          <p className="page-intro__description">
            콘텐츠 정보를 입력하면 콘텐츠 유형에 맞는 템플릿을 추천하고 카드뉴스
            제작을 진행합니다.
          </p>
        </section>

        <nav className="step-navigation" aria-label="카드뉴스 제작 단계">
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

          <div
            className={`step-item ${step >= 3 ? 'step-item--active' : ''} ${
              step > 3 ? 'step-item--completed' : ''
            }`}
          >
            <span className="step-item__number">03</span>

            <div className="step-item__content">
              <span className="step-item__label">템플릿 선택</span>

              <span className="step-item__description">추천 템플릿 확인</span>
            </div>
          </div>

          <span className="step-navigation__line" />

          <div className={`step-item ${step >= 4 ? 'step-item--active' : ''}`}>
            <span className="step-item__number">04</span>

            <div className="step-item__content">
              <span className="step-item__label">미리보기</span>

              <span className="step-item__description">생성 결과 확인</span>
            </div>
          </div>
        </nav>

        <section className="workflow-panel">
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

          {step === 3 && (
            <div className="workflow-step">
              {isCreating ? (
                <GenerationLoading />
              ) : previewError ? (
                <GenerationError
                  message={previewError}
                  onRetry={() => void handleCreateContent()}
                  onEditTemplate={() => void handleEditTemplate()}
                  retryDisabled={!isPostDataValid || !selectedTemplateId}
                />
              ) : (
                <>
                  <div className="workflow-step__header">
                    <p className="workflow-step__eyebrow">STEP 03 · TEMPLATE</p>

                    <h3 className="workflow-step__title">
                      추천 템플릿을 선택해주세요.
                    </h3>

                    <p className="workflow-step__description">
                      선택한 콘텐츠 유형에 적합한 템플릿을 확인하고 카드뉴스
                      제작에 사용할 디자인을 선택합니다.
                    </p>
                  </div>

                  <TemplateSelector contentType={selectedContentType} />

                  <div className="workflow-navigation">
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
                      disabled={!isPostDataValid || !selectedTemplateId}
                      onClick={handleCreateContent}
                    >
                      카드뉴스 만들기
                    </button>
                  </div>
                </>
              )}
            </div>
          )}

          {step === 4 && preview && preview.cardGenerationResult && (
            <div className="workflow-step">
              <div className="workflow-step__header">
                <p className="workflow-step__eyebrow">STEP 04 · PREVIEW</p>

                <h3 className="workflow-step__title">
                  생성된 카드뉴스를 확인해주세요.
                </h3>

                <p className="workflow-step__description">
                  카드별 문구를 확인하고 필요한 내용을 수정할 수 있습니다.
                </p>
              </div>

              <CardNewsEditor preview={preview} onUpdated={setPreview} />

              <div className="workflow-navigation">
                <button
                  type="button"
                  className="secondary-button"
                  onClick={handlePreviousStep}
                >
                  <span aria-hidden="true">←</span>
                  템플릿 수정
                </button>

                <button
                  type="button"
                  className="primary-button"
                  onClick={handleStartNewContent}
                >
                  새 카드뉴스 만들기
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
