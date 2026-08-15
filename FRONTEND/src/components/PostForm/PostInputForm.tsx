import { useEffect, useRef, useState } from 'react';
import styles from './PostInputForm.module.css';

export interface PostInputFormSubmitPayload {
  title: string;
  body: string;
  images: File[];
}

interface PostInputFormProps {
  onSubmit?: (payload: PostInputFormSubmitPayload) => void;
  //최대 업로드 가능 이미지 개수
  maxImages?: number;
  //이미지 1장당 최대 용량(MB)
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
  onSubmit,
  maxImages = 10,
  maxImageSizeMB = 10,
}: PostInputFormProps) {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [images, setImages] = useState<ImageItem[]>([]);
  const [isDragging, setIsDragging] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // 컴포넌트가 사라지거나 이미지 목록이 바뀔 때, 만들어둔 objectURL을 해제해서
  // 메모리 누수를 막는다. (브라우저가 만든 임시 미리보기 주소는 직접 정리해야 함)
  useEffect(() => {
    return () => {
      images.forEach((image) => URL.revokeObjectURL(image.previewUrl));
    };
  }, [images]);

  const isFormValid =
    title.trim().length > 0 && body.trim().length > 0 && images.length > 0;

  function handleTitleChange(event: React.ChangeEvent<HTMLInputElement>) {
    setTitle(event.target.value.slice(0, TITLE_MAX_LENGTH));
  }

  function handleBodyChange(event: React.ChangeEvent<HTMLTextAreaElement>) {
    setBody(event.target.value.slice(0, BODY_MAX_LENGTH));
  }

  function addFiles(fileList: FileList) {
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

    setImages((prev) => {
      const remainingSlots = maxImages - prev.length;
      if (remainingSlots <= 0) {
        setErrorMessage(`사진은 최대 ${maxImages}장까지 업로드할 수 있어요.`);
        return prev;
      }
      const filesToAdd = validFiles.slice(0, remainingSlots);
      const newItems: ImageItem[] = filesToAdd.map((file) => ({
        id: `${file.name}-${file.lastModified}-${Math.random()
          .toString(36)
          .slice(2, 8)}`,
        file,
        previewUrl: URL.createObjectURL(file),
      }));
      return [...prev, ...newItems];
    });

    setErrorMessage(rejectionReason);
  }

  function handleFileInputChange(event: React.ChangeEvent<HTMLInputElement>) {
    if (event.target.files && event.target.files.length > 0) {
      addFiles(event.target.files);
    }
    // 같은 파일을 다시 선택해도 change 이벤트가 발생하도록 값 초기화
    event.target.value = '';
  }

  function handleDrop(event: React.DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setIsDragging(false);
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

  function handleRemoveImage(id: string) {
    setImages((prev) => {
      const target = prev.find((image) => image.id === id);
      if (target) {
        URL.revokeObjectURL(target.previewUrl);
      }
      return prev.filter((image) => image.id !== id);
    });
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!isFormValid) {
      setErrorMessage('제목, 본문, 사진을 모두 입력해주세요.');
      return;
    }
    onSubmit?.({
      title: title.trim(),
      body: body.trim(),
      images: images.map((image) => image.file),
    });
  }

  return (
    <form className={styles.card} onSubmit={handleSubmit} noValidate>
      <header className={styles.header}>
        <span className={styles.eyebrow}>카드뉴스 자동 생성</span>
        <h1 className={styles.heading}>새 콘텐츠 만들기</h1>
        <p className={styles.subheading}>
          제목, 본문, 사진만 입력하면 자동으로 카드뉴스가 만들어져요.
        </p>
      </header>

      {/* 01. 제목 */}
      <section className={styles.field}>
        <div className={styles.fieldLabelRow}>
          <label htmlFor="post-title" className={styles.fieldLabel}>
            <span className={styles.fieldIndex}>01</span>
            제목
          </label>
          <span className={styles.counter}>
            {title.length} / {TITLE_MAX_LENGTH}
          </span>
        </div>
        <input
          id="post-title"
          type="text"
          className={styles.textInput}
          placeholder="예) 신입생을 위한 학회 활동 가이드"
          value={title}
          onChange={handleTitleChange}
          required
        />
      </section>

      {/* 02. 본문 */}
      <section className={styles.field}>
        <div className={styles.fieldLabelRow}>
          <label htmlFor="post-body" className={styles.fieldLabel}>
            <span className={styles.fieldIndex}>02</span>
            본문
          </label>
          <span className={styles.counter}>
            {body.length} / {BODY_MAX_LENGTH}
          </span>
        </div>
        <textarea
          id="post-body"
          className={styles.textArea}
          placeholder="카드뉴스에 들어갈 본문 내용을 입력하세요."
          value={body}
          onChange={handleBodyChange}
          rows={6}
          required
        />
      </section>

      {/* 03. 사진 */}
      <section className={styles.field}>
        <div className={styles.fieldLabelRow}>
          <span className={styles.fieldLabel}>
            <span className={styles.fieldIndex}>03</span>
            사진
          </span>
          <span className={styles.counter}>
            {images.length} / {maxImages}
          </span>
        </div>

        <div
          className={`${styles.dropzone} ${
            isDragging ? styles.dropzoneActive : ''
          }`}
          onDrop={handleDrop}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onClick={() => fileInputRef.current?.click()}
          role="button"
          tabIndex={0}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              fileInputRef.current?.click();
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

      <button
        type="submit"
        className={styles.submitButton}
        disabled={!isFormValid}
      >
        카드뉴스 만들기
      </button>
    </form>
  );
}

export default PostInputForm;
