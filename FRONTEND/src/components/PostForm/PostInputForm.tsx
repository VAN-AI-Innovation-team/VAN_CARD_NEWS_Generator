import { useEffect, useRef, useState } from 'react';
import styles from './PostInputForm.module.css';

export interface ExistingImageInput {
  id: number;
  imageUrl: string;
}

export interface PostInputFormSubmitPayload {
  title: string;
  body: string;
  images: File[];
  existingImageIds?: number[];
}

interface PostInputFormProps {
  onChange?: (payload: PostInputFormSubmitPayload) => void;

  // 이전 단계로 돌아왔을 때 기존 입력값 복원
  initialData?: PostInputFormSubmitPayload | null;

  // 기존 콘텐츠 수정 시 서버에 저장되어 있는 원본 이미지
  initialExistingImages?: ExistingImageInput[];

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
  initialExistingImages = [],
  maxImages = 10,
  maxImageSizeMB = 10,
}: PostInputFormProps) {
  const [title, setTitle] = useState(initialData?.title ?? '');
  const [body, setBody] = useState(initialData?.body ?? '');

  const [images, setImages] = useState<ImageItem[]>([]);

  const [existingImages, setExistingImages] = useState<ExistingImageInput[]>(
    [],
  );

  const [isDragging, setIsDragging] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  // 필드별 "사용자가 한 번이라도 상호작용했는지" 여부
  const [titleTouched, setTitleTouched] = useState(false);
  const [bodyTouched, setBodyTouched] = useState(false);
  const [imagesTouched, setImagesTouched] = useState(false);

  // 사용자가 글자수 제한을 초과해서 입력하려 한 경우
  const [titleLimitExceeded, setTitleLimitExceeded] = useState(false);
  const [bodyLimitExceeded, setBodyLimitExceeded] = useState(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  /**
   * 초기 데이터 복원
   *
   * 중요:
   * initialData는 부모의 onChange에 의해 계속 새로운 객체가 전달될 수 있습니다.
   * 따라서 [initialData, initialExistingImages]를 dependency로 두면
   * 사용자가 이미지를 삭제할 때마다 기존 이미지가 다시 복원될 수 있습니다.
   *
   * 현재 App 구조에서는 Step 2로 진입할 때 PostInputForm이 새로 마운트되므로
   * 최초 마운트 시점에만 초기값을 가져오면 됩니다.
   */
  useEffect(() => {
    setTitle(initialData?.title ?? '');
    setBody(initialData?.body ?? '');
    setExistingImages(initialExistingImages);

    const restoredImages: ImageItem[] = (initialData?.images ?? []).map(
      (file, index) => ({
        id: `${file.name}-${file.lastModified}-${index}`,
        file,
        previewUrl: URL.createObjectURL(file),
      }),
    );

    setImages(restoredImages);

    return () => {
      restoredImages.forEach((image) => {
        URL.revokeObjectURL(image.previewUrl);
      });
    };

    // 의도적으로 최초 마운트 시점의 초기값만 사용합니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /**
   * 컴포넌트가 완전히 사라질 때
   * 새로 선택한 이미지의 object URL을 정리합니다.
   */
  useEffect(() => {
    return () => {
      images.forEach((image) => {
        URL.revokeObjectURL(image.previewUrl);
      });
    };
  }, []);

  /**
   * 현재 입력값을 App으로 전달합니다.
   */
  function notifyChange(
    nextTitle: string,
    nextBody: string,
    nextImages: ImageItem[],
    nextExistingImages: ExistingImageInput[] = existingImages,
  ) {
    onChange?.({
      title: nextTitle,
      body: nextBody,
      images: nextImages.map((image) => image.file),
      existingImageIds: nextExistingImages.map((image) => image.id),
    });
  }

  // 필수 입력 검증 메시지
  const titleRequiredError =
    title.trim().length === 0 ? '제목을 입력해주세요.' : null;

  const bodyRequiredError =
    body.trim().length === 0 ? '본문을 입력해주세요.' : null;

  const totalImageCount = existingImages.length + images.length;

  const imagesRequiredError =
    totalImageCount === 0 ? '사진을 최소 1장 이상 등록해주세요.' : null;

  const showTitleRequiredError = titleTouched && Boolean(titleRequiredError);
  const showBodyRequiredError = bodyTouched && Boolean(bodyRequiredError);
  const showImagesRequiredError = imagesTouched && Boolean(imagesRequiredError);

  const isTitleAtLimit = title.length >= TITLE_MAX_LENGTH;
  const isBodyAtLimit = body.length >= BODY_MAX_LENGTH;

  function handleTitleChange(event: React.ChangeEvent<HTMLInputElement>) {
    const rawValue = event.target.value;
    const nextTitle = rawValue.slice(0, TITLE_MAX_LENGTH);

    setTitleLimitExceeded(rawValue.length > TITLE_MAX_LENGTH);
    setTitle(nextTitle);
    setTitleTouched(true);

    notifyChange(nextTitle, body, images, existingImages);
  }

  function handleBodyChange(event: React.ChangeEvent<HTMLTextAreaElement>) {
    const rawValue = event.target.value;
    const nextBody = rawValue.slice(0, BODY_MAX_LENGTH);

    setBodyLimitExceeded(rawValue.length > BODY_MAX_LENGTH);
    setBody(nextBody);
    setBodyTouched(true);

    notifyChange(title, nextBody, images, existingImages);
  }

  function handleTitleBlur() {
    setTitleTouched(true);
  }

  function handleBodyBlur() {
    setBodyTouched(true);
  }

  function addFiles(fileList: FileList) {
    setImagesTouched(true);
    setErrorMessage('');

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

    const remainingSlots = maxImages - existingImages.length - images.length;

    if (remainingSlots <= 0) {
      setErrorMessage(`사진은 최대 ${maxImages}장까지 업로드할 수 있어요.`);
      return;
    }

    const filesToAdd = validFiles.slice(0, remainingSlots);

    if (filesToAdd.length === 0) {
      if (rejectionReason) {
        setErrorMessage(rejectionReason);
      }

      return;
    }

    const newItems: ImageItem[] = filesToAdd.map((file) => ({
      id: `${file.name}-${file.lastModified}-${Math.random()
        .toString(36)
        .slice(2, 8)}`,
      file,
      previewUrl: URL.createObjectURL(file),
    }));

    const nextImages = [...images, ...newItems];

    setImages(nextImages);

    notifyChange(title, body, nextImages, existingImages);

    if (rejectionReason) {
      setErrorMessage(rejectionReason);
    }
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

  /**
   * 기존 서버 이미지 삭제
   *
   * 여기서 기존 이미지 배열을 먼저 갱신하고
   * 갱신된 배열을 notifyChange에 명시적으로 전달합니다.
   *
   * 따라서 부모 postData에도 삭제된 이미지 ID가 즉시 반영됩니다.
   */
  function handleRemoveExistingImage(id: number) {
    setImagesTouched(true);
    setErrorMessage('');

    const nextExistingImages = existingImages.filter(
      (image) => image.id !== id,
    );

    setExistingImages(nextExistingImages);

    notifyChange(title, body, images, nextExistingImages);
  }

  /**
   * 새로 선택한 이미지 삭제
   */
  function handleRemoveImage(id: string) {
    setImagesTouched(true);
    setErrorMessage('');

    const target = images.find((image) => image.id === id);

    const nextImages = images.filter((image) => image.id !== id);

    if (target) {
      URL.revokeObjectURL(target.previewUrl);
    }

    setImages(nextImages);

    notifyChange(title, body, nextImages, existingImages);
  }

  /**
   * 서버 이미지 URL이 깨진 경우
   *
   * 백엔드가 아직 실행되지 않았거나 이미지 URL에 접근할 수 없는 경우에도
   * 깨진 이미지 아이콘 대신 안내 문구를 보여줍니다.
   */
  function handleExistingImageError(
    event: React.SyntheticEvent<HTMLImageElement>,
  ) {
    const image = event.currentTarget;

    image.style.display = 'none';

    const parent = image.parentElement;

    if (!parent) {
      return;
    }

    const errorElement = parent.querySelector('[data-image-error]');

    if (errorElement instanceof HTMLElement) {
      errorElement.style.display = 'flex';
    }
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
            {totalImageCount} / {maxImages}
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
              event.preventDefault();
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

        {totalImageCount > 0 && (
          <ul className={styles.thumbnailGrid}>
            {/* 기존 서버 이미지 */}
            {existingImages.map((image) => (
              <li key={`existing-${image.id}`} className={styles.thumbnailItem}>
                <div
                  data-image-error
                  style={{
                    display: 'none',
                    width: '100%',
                    height: '100%',
                    alignItems: 'center',
                    justifyContent: 'center',
                    textAlign: 'center',
                    fontSize: '13px',
                    color: '#999',
                    padding: '12px',
                  }}
                >
                  이미지를 불러올 수 없습니다.
                </div>

                <img
                  src={image.imageUrl}
                  alt="기존 업로드 이미지"
                  className={styles.thumbnailImage}
                  onError={handleExistingImageError}
                />

                <button
                  type="button"
                  className={styles.thumbnailRemoveButton}
                  onClick={(event) => {
                    event.stopPropagation();
                    handleRemoveExistingImage(image.id);
                  }}
                  aria-label="기존 이미지 삭제"
                >
                  ×
                </button>
              </li>
            ))}

            {/* 새로 선택한 이미지 */}
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
                  onClick={(event) => {
                    event.stopPropagation();
                    handleRemoveImage(image.id);
                  }}
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
