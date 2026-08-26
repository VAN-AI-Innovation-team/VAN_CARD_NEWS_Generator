import { useEffect, useRef, useState } from 'react';

import type { CropArea, PreviewImage } from '../../api/contentApi';

import {
  DEFAULT_CROP_AREA,
  MIN_CROP_SIZE,
  normalizeCropArea,
} from './cropUtils';

import './ImageCropEditor.css';

interface ImageCropEditorProps {
  image: PreviewImage;
  cropArea: CropArea;
  onChange: (cropArea: CropArea) => void;
}

type InteractionMode = 'move' | 'resize' | null;

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

  useEffect(() => {
    if (!interactionRef.current.mode) {
      interactionRef.current.startCrop = normalizeCropArea(cropArea);
    }
  }, [cropArea]);

  useEffect(() => {
    return () => {
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerup', handlePointerUp);
    };
  }, []);

  function handlePointerMove(event: PointerEvent) {
    const imageElement = imageRef.current;

    if (!imageElement || !interactionRef.current.mode) {
      return;
    }

    const rect = imageElement.getBoundingClientRect();

    if (!rect.width || !rect.height) {
      return;
    }

    const deltaX =
      ((event.clientX - interactionRef.current.startX) / rect.width) * 100;

    const deltaY =
      ((event.clientY - interactionRef.current.startY) / rect.height) * 100;

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

  const safeCrop = normalizeCropArea(cropArea ?? DEFAULT_CROP_AREA);

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

      {/* 실제 크롭 영역 조정 창 */}
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

      {/* X / Y / W / H 직접 입력 */}
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
            min={MIN_CROP_SIZE}
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
            min={MIN_CROP_SIZE}
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
