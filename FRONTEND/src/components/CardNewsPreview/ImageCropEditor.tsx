import { useEffect, useRef, useState } from 'react';

import type { CropArea, PreviewImage } from '../../api/contentApi';

import './ImageCropEditor.css';

interface ImageCropEditorProps {
  image: PreviewImage;
  cropArea: CropArea;
  onChange: (cropArea: CropArea) => void;
}

type InteractionMode = 'move' | 'resize' | null;

const MIN_SIZE = 5;

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function normalizeCropArea(cropArea: CropArea): CropArea {
  const width = clamp(cropArea.width, MIN_SIZE, 100);
  const height = clamp(cropArea.height, MIN_SIZE, 100);

  return {
    width,
    height,
    x: clamp(cropArea.x, 0, 100 - width),
    y: clamp(cropArea.y, 0, 100 - height),
  };
}

export default function ImageCropEditor({
  image,
  cropArea,
  onChange,
}: ImageCropEditorProps) {
  const canvasRef = useRef<HTMLDivElement>(null);
  const imageRef = useRef<HTMLImageElement>(null);

  const interactionRef = useRef<{
    mode: InteractionMode;
    startX: number;
    startY: number;
    startCrop: CropArea;
  }>({
    mode: null,
    startX: 0,
    startY: 0,
    startCrop: normalizeCropArea(cropArea),
  });

  const [isInteracting, setIsInteracting] = useState(false);

  /*
   * 중요:
   * 드래그가 진행되는 동안에는 interactionRef.current.startCrop을
   * 현재 cropArea로 다시 덮어쓰지 않습니다.
   *
   * 이렇게 해야 mousemove마다 부모 state가 변경되더라도
   * 드래그 시작 시점의 crop 영역을 기준으로 계산할 수 있습니다.
   */
  useEffect(() => {
    if (!interactionRef.current.mode) {
      interactionRef.current.startCrop = normalizeCropArea(cropArea);
    }
  }, [cropArea]);

  function handlePointerMove(event: PointerEvent) {
    const imageElement = imageRef.current;

    if (!imageElement || !interactionRef.current.mode) {
      return;
    }

    const rect = imageElement.getBoundingClientRect();

    if (!rect.width || !rect.height) {
      return;
    }

    /*
     * 마우스가 실제 이미지에서 이동한 픽셀을
     * 이미지 기준 0~100% 좌표로 변환합니다.
     */
    const deltaX =
      ((event.clientX - interactionRef.current.startX) / rect.width) * 100;

    const deltaY =
      ((event.clientY - interactionRef.current.startY) / rect.height) * 100;

    /*
     * 드래그 시작 시점의 crop을 기준으로 계속 계산합니다.
     * 현재 cropArea를 기준으로 계산하지 않는 것이 중요합니다.
     */
    const start = interactionRef.current.startCrop;

    if (interactionRef.current.mode === 'move') {
      onChange(
        normalizeCropArea({
          x: start.x + deltaX,
          y: start.y + deltaY,
          width: start.width,
          height: start.height,
        }),
      );

      return;
    }

    onChange(
      normalizeCropArea({
        x: start.x,
        y: start.y,
        width: start.width + deltaX,
        height: start.height + deltaY,
      }),
    );
  }

  function handlePointerUp() {
    interactionRef.current.mode = null;

    setIsInteracting(false);

    window.removeEventListener('pointermove', handlePointerMove);
    window.removeEventListener('pointerup', handlePointerUp);
  }

  function startInteraction(
    event: React.PointerEvent<HTMLElement>,
    mode: Exclude<InteractionMode, null>,
  ) {
    event.preventDefault();
    event.stopPropagation();

    /*
     * 드래그가 시작되는 순간의 crop 값을 고정합니다.
     */
    interactionRef.current = {
      mode,
      startX: event.clientX,
      startY: event.clientY,
      startCrop: normalizeCropArea(cropArea),
    };

    setIsInteracting(true);

    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('pointerup', handlePointerUp);
  }

  const safeCrop = normalizeCropArea(cropArea);

  return (
    <div className="image-crop-editor">
      <div className="image-crop-editor__header">
        <div>
          <strong>이미지 크롭 영역</strong>

          <span>영역을 드래그하거나 우측 하단 핸들로 크기를 조절하세요.</span>
        </div>

        <span className="image-crop-editor__image-name">
          이미지 {image.sortOrder + 1}
        </span>
      </div>

      <div
        ref={canvasRef}
        className={`image-crop-editor__canvas ${
          isInteracting ? 'image-crop-editor__canvas--dragging' : ''
        }`}
      >
        <img ref={imageRef} src={image.imageUrl} alt="" draggable={false} />

        <div
          className="image-crop-editor__selection"
          style={{
            left: `${safeCrop.x}%`,
            top: `${safeCrop.y}%`,
            width: `${safeCrop.width}%`,
            height: `${safeCrop.height}%`,
          }}
          onPointerDown={(event) => startInteraction(event, 'move')}
        >
          <span className="image-crop-editor__selection-label">선택 영역</span>

          <button
            type="button"
            className="image-crop-editor__resize-handle"
            aria-label="크롭 영역 크기 조절"
            onPointerDown={(event) => startInteraction(event, 'resize')}
          />
        </div>
      </div>

      <div className="image-crop-editor__values">
        <label>
          <span>X</span>

          <input
            type="number"
            min={0}
            max={100}
            step={0.1}
            value={safeCrop.x.toFixed(1)}
            onChange={(event) =>
              onChange(
                normalizeCropArea({
                  ...safeCrop,
                  x: Number(event.target.value),
                }),
              )
            }
          />

          <small>%</small>
        </label>

        <label>
          <span>Y</span>

          <input
            type="number"
            min={0}
            max={100}
            step={0.1}
            value={safeCrop.y.toFixed(1)}
            onChange={(event) =>
              onChange(
                normalizeCropArea({
                  ...safeCrop,
                  y: Number(event.target.value),
                }),
              )
            }
          />

          <small>%</small>
        </label>

        <label>
          <span>W</span>

          <input
            type="number"
            min={MIN_SIZE}
            max={100}
            step={0.1}
            value={safeCrop.width.toFixed(1)}
            onChange={(event) =>
              onChange(
                normalizeCropArea({
                  ...safeCrop,
                  width: Number(event.target.value),
                }),
              )
            }
          />

          <small>%</small>
        </label>

        <label>
          <span>H</span>

          <input
            type="number"
            min={MIN_SIZE}
            max={100}
            step={0.1}
            value={safeCrop.height.toFixed(1)}
            onChange={(event) =>
              onChange(
                normalizeCropArea({
                  ...safeCrop,
                  height: Number(event.target.value),
                }),
              )
            }
          />

          <small>%</small>
        </label>
      </div>
    </div>
  );
}
