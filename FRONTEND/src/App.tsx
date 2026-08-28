import { useEffect, useState } from 'react';
import axios from 'axios';

import { TemplateProvider, useTemplate } from './contexts/TemplateContext';

import { TemplateSelector } from './components/TemplateSelector/TemplateSelector';
import CardNewsEditor from './components/CardNewsPreview/CardNewsEditor';
import ContentResult from './components/ContentResult/ContentResult';
import PostInputForm, {
  type PostInputFormSubmitPayload,
} from './components/PostForm/PostInputForm';
import GenerationLoading from './components/GenerationState/GenerationLoading';
import GenerationError from './components/GenerationState/GenerationError';
import ApprovalList from './components/ApprovalReview/ApprovalList';
import ApprovalReview from './components/ApprovalReview/ApprovalReview';
import type { ApprovalRequestListItem } from './api/approvalApi';
import ContentManagement from './components/ContentManagement/ContentManagement';
import ContentManagementDetail from './components/ContentManagement/ContentManagementDetail';
import type { ContentManagementListItem } from './api/contentApi';

import ContentTypeSelector from './components/ContentTypeSelector/ContentTypeSelector';

import type { ContentType } from './types/content';

import {
  createContent,
  editContent,
  cloneContent,
  fetchContentPreview,
  generateCardImages,
  fetchGeneratedCardImages,
  type GeneratedCardImageResponse,
  type ContentPreviewResponse,
} from './api/contentApi';

import './App.css';

type Step = 1 | 2 | 3 | 4 | 5;
type AppScreen =
  | 'create'
  | 'approval-list'
  | 'approval-review'
  | 'content-management'
  | 'content-management-detail';

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
  const [screen, setScreen] = useState<AppScreen>('create');
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [selectedApprovalRequest, setSelectedApprovalRequest] =
    useState<ApprovalRequestListItem | null>(null);
  const [selectedManagedContent, setSelectedManagedContent] =
    useState<ContentManagementListItem | null>(null);

  const [serverConnected, setServerConnected] = useState<boolean | null>(null);

  const [selectedContentType, setSelectedContentType] =
    useState<ContentType | null>(null);

  const [postData, setPostData] = useState<PostInputFormSubmitPayload | null>(
    null,
  );
  const [editingContentId, setEditingContentId] = useState<number | null>(null);
  const [editingExistingImages, setEditingExistingImages] = useState<
    { id: number; imageUrl: string }[]
  >([]);

  const [isCreating, setIsCreating] = useState(false);

  const [preview, setPreview] = useState<ContentPreviewResponse | null>(null);

  const [previewError, setPreviewError] = useState<string | null>(null);

  const [generatedImages, setGeneratedImages] = useState<
    GeneratedCardImageResponse[]
  >([]);
  const [isGeneratingImages, setIsGeneratingImages] = useState(false);
  const [generationImageError, setGenerationImageError] = useState<
    string | null
  >(null);

  const { selectedTemplateId, setSelectedTemplateId, clearSelectedTemplate } =
    useTemplate();

  const isPostDataValid =
    postData !== null &&
    postData.title.trim().length > 0 &&
    postData.body.trim().length > 0 &&
    postData.images.length + (postData.existingImageIds?.length ?? 0) > 0;

  async function checkBackendConnection() {
    try {
      await axios.get('/api/health');

      setServerConnected(true);
    } catch (error) {
      console.error('백엔드 통신 오류:', error);
      setServerConnected(false);
    }
  }

  const ACTIVE_CONTENT_ID_KEY = 'van-card-news-active-content-id';

  async function restoreActiveContent() {
    const params = new URLSearchParams(window.location.search);
    const queryId = Number(params.get('contentId'));
    const storedId = Number(localStorage.getItem(ACTIVE_CONTENT_ID_KEY));
    const contentId =
      Number.isInteger(queryId) && queryId > 0 ? queryId : storedId;

    if (!Number.isInteger(contentId) || contentId <= 0) {
      return;
    }

    try {
      const restoredPreview = await fetchContentPreview(contentId);

      setPreview(restoredPreview);
      setSelectedContentType(
        restoredPreview.template.contentType as ContentType,
      );
      setSelectedTemplateId(restoredPreview.template.id);
      setEditingContentId(contentId);
      setPostData({
        title: restoredPreview.title,
        body: restoredPreview.body,
        images: [],
        existingImageIds: restoredPreview.images.map((image) => image.id),
      });
      setEditingExistingImages(
        restoredPreview.images.map((image) => ({
          id: image.id,
          imageUrl: image.imageUrl,
        })),
      );

      if (!restoredPreview.cardGenerationResult) {
        setGeneratedImages([]);
        setGenerationImageError(null);
        setPreviewError(null);
        setStep(2);
        setScreen('create');
        localStorage.setItem(ACTIVE_CONTENT_ID_KEY, String(contentId));
        window.history.replaceState(null, '', `/?contentId=${contentId}`);
        return;
      }

      const restoredImages = await fetchGeneratedCardImages(contentId);
      setGeneratedImages(restoredImages);
      setStep(restoredImages.length > 0 ? 5 : 4);
      setScreen('create');

      localStorage.setItem(ACTIVE_CONTENT_ID_KEY, String(contentId));
      window.history.replaceState(null, '', `/?contentId=${contentId}`);
    } catch (error) {
      console.error('기존 생성 결과 복원 실패:', error);
      localStorage.removeItem(ACTIVE_CONTENT_ID_KEY);
    }
  }

  useEffect(() => {
    void checkBackendConnection();
    void restoreActiveContent();
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

      const response = editingContentId
        ? await editContent(editingContentId, {
            title: postData.title,
            body: postData.body,
            images: postData.images,
            templateId: selectedTemplateId,
            keepImageIds: postData.existingImageIds ?? [],
          })
        : await createContent({
            title: postData.title,
            body: postData.body,
            images: postData.images,
            templateId: selectedTemplateId,
          });

      const previewData = await waitForPreview(response.contentId);

      localStorage.setItem(ACTIVE_CONTENT_ID_KEY, String(response.contentId));

      window.history.replaceState(
        null,
        '',
        `/?contentId=${response.contentId}`,
      );

      setPreview(previewData);
      setGeneratedImages([]);
      setGenerationImageError(null);
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

  async function handleGenerateCardImages(
    updatedPreview: ContentPreviewResponse,
  ) {
    try {
      setPreview(updatedPreview);
      setGenerationImageError(null);
      setGeneratedImages([]);
      setIsGeneratingImages(true);
      setStep(5);

      const images = await generateCardImages(updatedPreview.contentId);
      setGeneratedImages(images);
    } catch (error) {
      console.error('Higgsfield 카드 이미지 생성 실패:', error);
      setGenerationImageError(
        error instanceof Error
          ? error.message
          : '카드뉴스 이미지 생성 중 오류가 발생했습니다.',
      );
    } finally {
      setIsGeneratingImages(false);
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
      return;
    }

    if (step === 5) {
      setStep(4);
    }
  }

  function handleEditTemplate() {
    setPreviewError(null);
    setStep(1);
  }

  function handleEditManagedContent(previewData: ContentPreviewResponse) {
    setEditingContentId(previewData.contentId);
    setSelectedContentType(previewData.template.contentType as ContentType);
    setPostData({
      title: previewData.title,
      body: previewData.body,
      images: [],
      existingImageIds: previewData.images.map((image) => image.id),
    });
    setEditingExistingImages(
      previewData.images.map((image) => ({
        id: image.id,
        imageUrl: image.imageUrl,
      })),
    );
    setPreview(previewData);
    setGeneratedImages([]);
    setPreviewError(null);
    setGenerationImageError(null);
    setStep(1);
    setScreen('create');
    clearSelectedTemplate();
    setSelectedTemplateId(previewData.template.id);
  }

  async function handleCloneAndRegenerate(contentId: number) {
    try {
      setPreviewError(null);
      setGenerationImageError(null);
      setIsCreating(true);

      const response = await cloneContent(contentId, true);
      const previewData = await waitForPreview(response.contentId);
      const clonedImages = await fetchGeneratedCardImages(response.contentId);

      setSelectedContentType(previewData.template.contentType as ContentType);
      setSelectedTemplateId(previewData.template.id);
      setEditingContentId(response.contentId);
      setPostData({
        title: previewData.title,
        body: previewData.body,
        images: [],
        existingImageIds: previewData.images.map((image) => image.id),
      });
      setEditingExistingImages(
        previewData.images.map((image) => ({
          id: image.id,
          imageUrl: image.imageUrl,
        })),
      );
      setPreview(previewData);
      setGeneratedImages(clonedImages);
      setScreen('create');
      setStep(2);

      localStorage.setItem(ACTIVE_CONTENT_ID_KEY, String(response.contentId));
      window.history.replaceState(
        null,
        '',
        `/?contentId=${response.contentId}`,
      );
    } catch (error) {
      console.error('콘텐츠 복제 및 재생성 실패:', error);
      const message =
        error instanceof Error
          ? error.message
          : '콘텐츠 복제 및 재생성에 실패했습니다.';
      setPreviewError(message);
      throw new Error(message);
    } finally {
      setIsCreating(false);
    }
  }

  function handleStartNewContent() {
    localStorage.removeItem(ACTIVE_CONTENT_ID_KEY);
    window.history.replaceState(null, '', window.location.pathname);
    setStep(1);
    setSelectedContentType(null);
    setPostData(null);
    setEditingContentId(null);
    setEditingExistingImages([]);
    setPreview(null);
    setPreviewError(null);
    setGeneratedImages([]);
    setGenerationImageError(null);
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

          <div className="app-header__actions">
            <div className="connection-status" aria-live="polite">
              <span
                className={`connection-status__dot ${
                  serverConnected === null
                    ? 'connection-status__dot--loading'
                    : serverConnected
                      ? 'connection-status__dot--success'
                      : 'connection-status__dot--error'
                }`}
              />

              <span className="connection-status__text">
                {serverConnected === null
                  ? '서버 확인 중'
                  : serverConnected
                    ? '서버 연결 원활'
                    : '서버 연결 실패'}
              </span>

              {serverConnected === false && (
                <button
                  type="button"
                  className="connection-status__button"
                  onClick={() => void checkBackendConnection()}
                >
                  다시 확인
                </button>
              )}
            </div>

            <button
              type="button"
              className="menu-button"
              aria-label="메뉴 열기"
              aria-expanded={isMenuOpen}
              onClick={() => setIsMenuOpen((open) => !open)}
            >
              <span />
              <span />
              <span />
            </button>
          </div>

          {isMenuOpen && (
            <aside className="app-sidebar">
              <div className="app-sidebar__header">
                <strong>메뉴</strong>

                <button
                  type="button"
                  aria-label="메뉴 닫기"
                  onClick={() => setIsMenuOpen(false)}
                >
                  ×
                </button>
              </div>

              <button
                type="button"
                className={`app-sidebar__item ${
                  screen === 'content-management' ||
                  screen === 'content-management-detail'
                    ? 'app-sidebar__item--active'
                    : ''
                }`}
                onClick={() => {
                  setScreen('content-management');
                  setSelectedManagedContent(null);
                  setIsMenuOpen(false);
                }}
              >
                <span>콘텐츠 관리</span>
                <small>생성 콘텐츠 확인·다운로드</small>
              </button>

              <button
                type="button"
                className={`app-sidebar__item ${
                  screen === 'approval-list' || screen === 'approval-review'
                    ? 'app-sidebar__item--active'
                    : ''
                }`}
                onClick={() => {
                  setScreen('approval-list');
                  setSelectedApprovalRequest(null);
                  setIsMenuOpen(false);
                }}
              >
                <span>검수 · 승인</span>
                <small>승인 요청 작업물 확인</small>
              </button>

              <button
                type="button"
                className={`app-sidebar__item ${
                  screen === 'create' ? 'app-sidebar__item--active' : ''
                }`}
                onClick={() => {
                  setScreen('create');
                  setIsMenuOpen(false);
                }}
              >
                <span>카드뉴스 제작</span>
                <small>새 카드뉴스 만들기</small>
              </button>
            </aside>
          )}
        </div>
      </header>

      <main className="app-main">
        {screen === 'create' ? (
          <>
            <section className="page-intro">
              <p className="page-intro__eyebrow">CARD NEWS GENERATOR</p>

              <h2 className="page-intro__title">새 카드뉴스 제작</h2>

              <p className="page-intro__description">
                콘텐츠 정보를 입력하면 콘텐츠 유형에 맞는 템플릿을 추천하고
                카드뉴스 제작을 진행합니다.
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
                  <span className="step-item__description">
                    추천 템플릿 확인
                  </span>
                </div>
              </div>

              <span className="step-navigation__line" />

              <div
                className={`step-item ${step >= 4 ? 'step-item--active' : ''} ${
                  step > 4 ? 'step-item--completed' : ''
                }`}
              >
                <span className="step-item__number">04</span>

                <div className="step-item__content">
                  <span className="step-item__label">카드 구성 확인</span>
                  <span className="step-item__description">카드 내용 수정</span>
                </div>
              </div>

              <span className="step-navigation__line" />

              <div
                className={`step-item ${step >= 5 ? 'step-item--active' : ''}`}
              >
                <span className="step-item__number">05</span>

                <div className="step-item__content">
                  <span className="step-item__label">생성 결과</span>
                  <span className="step-item__description">최종 결과 확인</span>
                </div>
              </div>
            </nav>

            <section className="workflow-panel">
              {step === 1 && (
                <div className="workflow-step">
                  <div className="workflow-step__header">
                    <p className="workflow-step__eyebrow">
                      STEP 01 · CONTENT TYPE
                    </p>

                    <h3 className="workflow-step__title">
                      콘텐츠 유형을 선택해주세요.
                    </h3>

                    <p className="workflow-step__description">
                      선택한 콘텐츠 유형을 기준으로 이후 단계에서 적합한
                      카드뉴스 템플릿을 추천합니다.
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

                  <PostInputForm
                    initialData={postData}
                    initialExistingImages={editingExistingImages}
                    onChange={setPostData}
                  />

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
                        <p className="workflow-step__eyebrow">
                          STEP 03 · TEMPLATE
                        </p>

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
                          카드 구성 확인
                        </button>
                      </div>
                    </>
                  )}
                </div>
              )}

              {step === 4 && preview && preview.cardGenerationResult && (
                <div className="workflow-step">
                  <div className="workflow-step__header">
                    <p className="workflow-step__eyebrow">
                      STEP 04 · CARD COMPOSITION
                    </p>

                    <h3 className="workflow-step__title">
                      생성된 카드 구성을 확인해주세요.
                    </h3>

                    <p className="workflow-step__description">
                      카드별 문구와 이미지를 확인하고 필요한 내용을 수정한 후
                      카드뉴스를 생성합니다.
                    </p>
                  </div>

                  <CardNewsEditor
                    preview={preview}
                    onUpdated={setPreview}
                    onGenerate={handleGenerateCardImages}
                  />

                  <div className="workflow-navigation">
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={handlePreviousStep}
                    >
                      <span aria-hidden="true">←</span>
                      템플릿 수정
                    </button>
                  </div>
                </div>
              )}

              {step === 5 && preview && (
                <div className="workflow-step">
                  <div className="workflow-step__header">
                    <p className="workflow-step__eyebrow">STEP 05 · RESULT</p>

                    <h3 className="workflow-step__title">
                      생성된 카드뉴스를 확인해주세요.
                    </h3>

                    <p className="workflow-step__description">
                      생성된 최종 카드 이미지를 확인하고 승인 요청을 진행할 수
                      있습니다.
                    </p>
                  </div>

                  <ContentResult
                    preview={preview}
                    images={generatedImages}
                    isLoading={isGeneratingImages}
                    error={generationImageError}
                  />

                  <div className="workflow-navigation">
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={handlePreviousStep}
                      disabled={isGeneratingImages}
                    >
                      <span aria-hidden="true">←</span>
                      카드 구성 수정
                    </button>

                    <button
                      type="button"
                      className="primary-button"
                      onClick={handleStartNewContent}
                      disabled={isGeneratingImages}
                    >
                      새 카드뉴스 만들기
                    </button>
                  </div>
                </div>
              )}
            </section>
          </>
        ) : screen === 'content-management' ? (
          <ContentManagement
            onSelect={(content) => {
              setSelectedManagedContent(content);
              setScreen('content-management-detail');
            }}
          />
        ) : screen === 'content-management-detail' && selectedManagedContent ? (
          <ContentManagementDetail
            content={selectedManagedContent}
            onBack={() => setScreen('content-management')}
            onUpdated={() => undefined}
            onEdit={handleEditManagedContent}
            onCloneAndRegenerate={handleCloneAndRegenerate}
          />
        ) : screen === 'approval-list' ? (
          <ApprovalList
            onSelect={(request) => {
              setSelectedApprovalRequest(request);
              setScreen('approval-review');
            }}
          />
        ) : selectedApprovalRequest ? (
          <ApprovalReview
            request={selectedApprovalRequest}
            onBack={() => setScreen('approval-list')}
            onCompleted={() => {
              // 처리 직후 목록에서 상태가 반영되도록 상세 화면의 목록 이동을 유지합니다.
            }}
          />
        ) : null}
      </main>

      <footer className="app-footer">
        <span>VAN Card News Generator</span>
        <span>AI 콘텐츠 자동 제작 시스템</span>
      </footer>
    </div>
  );
}

export default App;
