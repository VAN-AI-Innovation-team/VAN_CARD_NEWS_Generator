import { useEffect, useRef, useState } from 'react';
import styles from './PostInputForm.module.css';

export interface PostInputFormSubmitPayload {
  title: string;
  body: string;
  images: File[];
}

interface PostInputFormProps {
  onChange?: (payload: PostInputFormSubmitPayload) => void;

  // 이전 단계로 돌아왔을 때 기존 입력값 복원
  initialData?: PostInputFormSubmitPayload | null;

  // 최대 업로드 가능 이미지 개수
  maxImages?: number;

  // 이미지 1장당 최대 용량(MB)
  maxImageSizeMB?: number;
}

interface ImageItem {
  id: string;
  file: File;
  previewUrl: string;
}

const TITLE_MAX_LENGTH = 40;
const BODY_MAX_LENGTH = 1000;

function PostInputForm({
  onChange,
  initialData,
  maxImages = 10,
  maxImageSizeMB = 10,
}: PostInputFormProps) {
  const [title, setTitle] = useState(initialData?.title ?? '');
  const [body, setBody] = useState(initialData?.body ?? '');
  const [images, setImages] = useState<ImageItem[]>([]);
  const [isDragging, setIsDragging] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  // 필드별 "사용자가 한 번이라도 상호작용했는지" 여부.
  // 값이 true가 되기 전까지는 필수 입력 에러를 보여주지 않아,
  // 폼 진입 직후부터 에러가 한꺼번에 노출되는 것을 방지합니다.
  const [titleTouched, setTitleTouched] = useState(false);
  const [bodyTouched, setBodyTouched] = useState(false);
  const [imagesTouched, setImagesTouched] = useState(false);

  // 사용자가 글자수 제한을 "초과해서 입력을 시도한 바로 그 순간"에만 true가 됩니다.
  // (제한까지 정상적으로 채운 것과, 제한을 넘겨서 입력하려 한 것을 구분하기 위함)
  const [titleLimitExceeded, setTitleLimitExceeded] = useState(false);
  const [bodyLimitExceeded, setBodyLimitExceeded] = useState(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  /**
   * 이전 단계에서 돌아왔을 때
   * App에서 저장하고 있던 입력값을 복원합니다.
   */
  useEffect(() => {
    if (!initialData) {
      return;
    }

    setTitle(initialData.title);
    setBody(initialData.body);

    const restoredImages: ImageItem[] = initialData.images.map(
      (file, index) => ({
        id: `${file.name}-${file.lastModified}-${index}`,
        file,
        previewUrl: URL.createObjectURL(file),
      }),
    );

    setImages((previousImages) => {
      previousImages.forEach((image) => {
        URL.revokeObjectURL(image.previewUrl);
      });

      return restoredImages;
    });
  }, [initialData]);

  /**
   * 컴포넌트가 사라지거나 이미지 목록이 변경될 때
   * object URL을 정리합니다.
   */
  useEffect(() => {
    return () => {
      images.forEach((image) => {
        URL.revokeObjectURL(image.previewUrl);
      });
    };
  }, [images]);

  /**
   * 현재 입력값을 App으로 전달합니다.
   */
  function notifyChange(
    nextTitle: string,
    nextBody: string,
    nextImages: ImageItem[],
  ) {
    onChange?.({
      title: nextTitle,
      body: nextBody,
      images: nextImages.map((image) => image.file),
    });
  }

  // 필수 입력 검증 메시지 (trim 기준 — App.tsx의 isPostDataValid와 동일한 기준)
  const titleRequiredError =
    title.trim().length === 0 ? '제목을 입력해주세요.' : null;

  const bodyRequiredError =
    body.trim().length === 0 ? '본문을 입력해주세요.' : null;

  const imagesRequiredError =
    images.length === 0 ? '사진을 최소 1장 이상 등록해주세요.' : null;

  const showTitleRequiredError = titleTouched && Boolean(titleRequiredError);
  const showBodyRequiredError = bodyTouched && Boolean(bodyRequiredError);
  const showImagesRequiredError = imagesTouched && Boolean(imagesRequiredError);

  const isTitleAtLimit = title.length >= TITLE_MAX_LENGTH;
  const isBodyAtLimit = body.length >= BODY_MAX_LENGTH;

  function handleTitleChange(event: React.ChangeEvent<HTMLInputElement>) {
    const rawValue = event.target.value;
    const nextTitle = rawValue.slice(0, TITLE_MAX_LENGTH);

    // 실제로 제한을 넘겨 입력(타이핑/붙여넣기)하려 한 경우에만 에러를 켭니다.
    // 지우거나 제한 이하로 입력 중이면 자동으로 꺼집니다.
    setTitleLimitExceeded(rawValue.length > TITLE_MAX_LENGTH);

    setTitle(nextTitle);
    setTitleTouched(true);

    notifyChange(nextTitle, body, images);
  }

  function handleBodyChange(event: React.ChangeEvent<HTMLTextAreaElement>) {
    const rawValue = event.target.value;
    const nextBody = rawValue.slice(0, BODY_MAX_LENGTH);

    setBodyLimitExceeded(rawValue.length > BODY_MAX_LENGTH);

    setBody(nextBody);
    setBodyTouched(true);

    notifyChange(title, nextBody, images);
  }

  function handleTitleBlur() {
    setTitleTouched(true);
  }

  function handleBodyBlur() {
    setBodyTouched(true);
  }

  function addFiles(fileList: FileList) {
    setImagesTouched(true);

    const incomingFiles = Array.from(fileList);

    const validFiles: File[] = [];

    let rejectionReason = '';

    for (const file of incomingFiles) {
      if (!file.type.startsWith('image/')) {
        rejectionReason = '이미지 파일만 업로드할 수 있어요.';
        continue;
      }

      if (file.size > maxImageSizeMB * 1024 * 1024) {
        rejectionReason = `이미지 1장당 최대 ${maxImageSizeMB}MB까지 업로드할 수 있어요.`;
        continue;
      }

      validFiles.push(file);
    }

    setImages((previousImages) => {
      const remainingSlots = maxImages - previousImages.length;

      if (remainingSlots <= 0) {
        setErrorMessage(`사진은 최대 ${maxImages}장까지 업로드할 수 있어요.`);

        return previousImages;
      }

      const filesToAdd = validFiles.slice(0, remainingSlots);

      const newItems: ImageItem[] = filesToAdd.map((file) => ({
        id: `${file.name}-${file.lastModified}-${Math.random()
          .toString(36)
          .slice(2, 8)}`,
        file,
        previewUrl: URL.createObjectURL(file),
      }));

      const nextImages = [...previousImages, ...newItems];

      notifyChange(title, body, nextImages);

      return nextImages;
    });

    setErrorMessage(rejectionReason);
  }

  function handleFileInputChange(event: React.ChangeEvent<HTMLInputElement>) {
    if (event.target.files && event.target.files.length > 0) {
      addFiles(event.target.files);
    }

    // 같은 파일을 다시 선택해도 change 이벤트가 발생하도록 초기화
    event.target.value = '';
  }

  function handleDrop(event: React.DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setIsDragging(false);
    setImagesTouched(true);

    if (event.dataTransfer.files && event.dataTransfer.files.length > 0) {
      addFiles(event.dataTransfer.files);
    }
  }

  function handleDragOver(event: React.DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setIsDragging(true);
  }

  function handleDragLeave() {
    setIsDragging(false);
  }

  function handleDropzoneOpen() {
    setImagesTouched(true);
    fileInputRef.current?.click();
  }

  function handleRemoveImage(id: string) {
    setImagesTouched(true);

    setImages((previousImages) => {
      const target = previousImages.find((image) => image.id === id);

      if (target) {
        URL.revokeObjectURL(target.previewUrl);
      }

      const nextImages = previousImages.filter((image) => image.id !== id);

      notifyChange(title, body, nextImages);

      return nextImages;
    });
  }

  return (
    <div className={styles.card}>
      <header className={styles.header}>
        <span className={styles.eyebrow}>카드뉴스 자동 생성</span>

        <h1 className={styles.heading}>새 콘텐츠 만들기</h1>

        <p className={styles.subheading}>
          제목, 본문, 사진을 입력한 후 다음 단계에서 사용할 템플릿을 선택합니다.
        </p>
      </header>

      {/* 01. 제목 */}
      <section className={styles.field}>
        <div className={styles.fieldLabelRow}>
          <label htmlFor="post-title" className={styles.fieldLabel}>
            <span className={styles.fieldIndex}>01</span>
            제목
            <span className={styles.requiredMark} aria-hidden="true">
              *
            </span>
          </label>

          <span
            className={`${styles.counter} ${
              isTitleAtLimit ? styles.counterAtLimit : ''
            }`}
          >
            {title.length} / {TITLE_MAX_LENGTH}
          </span>
        </div>

        <input
          id="post-title"
          type="text"
          className={`${styles.textInput} ${
            showTitleRequiredError || titleLimitExceeded
              ? styles.inputInvalid
              : ''
          }`}
          placeholder="예) 신입생을 위한 학회 활동 가이드"
          value={title}
          onChange={handleTitleChange}
          onBlur={handleTitleBlur}
          aria-invalid={showTitleRequiredError || titleLimitExceeded}
          aria-describedby="post-title-error"
          required
        />

        <p id="post-title-error" className={styles.fieldErrorText} role="alert">
          {titleLimitExceeded
            ? `최대 ${TITLE_MAX_LENGTH}자까지 입력할 수 있어요. 초과한 내용은 저장되지 않습니다.`
            : showTitleRequiredError
              ? titleRequiredError
              : ''}
        </p>
      </section>

      {/* 02. 본문 */}
      <section className={styles.field}>
        <div className={styles.fieldLabelRow}>
          <label htmlFor="post-body" className={styles.fieldLabel}>
            <span className={styles.fieldIndex}>02</span>
            본문
            <span className={styles.requiredMark} aria-hidden="true">
              *
            </span>
          </label>

          <span
            className={`${styles.counter} ${
              isBodyAtLimit ? styles.counterAtLimit : ''
            }`}
          >
            {body.length} / {BODY_MAX_LENGTH}
          </span>
        </div>

        <textarea
          id="post-body"
          className={`${styles.textArea} ${
            showBodyRequiredError || bodyLimitExceeded
              ? styles.inputInvalid
              : ''
          }`}
          placeholder="카드뉴스에 들어갈 본문 내용을 입력하세요."
          value={body}
          onChange={handleBodyChange}
          onBlur={handleBodyBlur}
          rows={6}
          aria-invalid={showBodyRequiredError || bodyLimitExceeded}
          aria-describedby="post-body-error"
          required
        />

        <p id="post-body-error" className={styles.fieldErrorText} role="alert">
          {bodyLimitExceeded
            ? `최대 ${BODY_MAX_LENGTH}자까지 입력할 수 있어요. 초과한 내용은 저장되지 않습니다.`
            : showBodyRequiredError
              ? bodyRequiredError
              : ''}
        </p>
      </section>

      {/* 03. 사진 */}
      <section className={styles.field}>
        <div className={styles.fieldLabelRow}>
          <span className={styles.fieldLabel}>
            <span className={styles.fieldIndex}>03</span>
            사진
            <span className={styles.requiredMark} aria-hidden="true">
              *
            </span>
          </span>

          <span className={styles.counter}>
            {images.length} / {maxImages}
          </span>
        </div>

        <div
          className={`${styles.dropzone} ${
            isDragging ? styles.dropzoneActive : ''
          } ${showImagesRequiredError ? styles.dropzoneInvalid : ''}`}
          onDrop={handleDrop}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onClick={handleDropzoneOpen}
          role="button"
          tabIndex={0}
          aria-invalid={showImagesRequiredError}
          aria-describedby="post-images-error"
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              handleDropzoneOpen();
            }
          }}
        >
          <p className={styles.dropzoneText}>
            사진을 이곳에 끌어다 놓거나 <span>클릭해서 선택</span>하세요.
          </p>

          <p className={styles.dropzoneHint}>
            JPG, PNG · 장당 최대 {maxImageSizeMB}MB · 최대 {maxImages}장
          </p>

          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            multiple
            className={styles.hiddenFileInput}
            onChange={handleFileInputChange}
          />
        </div>

        <p
          id="post-images-error"
          className={styles.fieldErrorText}
          role="alert"
        >
          {showImagesRequiredError ? imagesRequiredError : ''}
        </p>

        {images.length > 0 && (
          <ul className={styles.thumbnailGrid}>
            {images.map((image) => (
              <li key={image.id} className={styles.thumbnailItem}>
                <img
                  src={image.previewUrl}
                  alt={image.file.name}
                  className={styles.thumbnailImage}
                />

                <button
                  type="button"
                  className={styles.thumbnailRemoveButton}
                  onClick={() => handleRemoveImage(image.id)}
                  aria-label={`${image.file.name} 삭제`}
                >
                  ×
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {errorMessage && <p className={styles.errorText}>{errorMessage}</p>}
    </div>
  );
}

export default PostInputForm;
