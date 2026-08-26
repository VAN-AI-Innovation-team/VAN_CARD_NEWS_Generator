import type { CSSProperties } from 'react';

import type { CropArea } from '../../api/contentApi';

export const DEFAULT_CROP_AREA: CropArea = {
  x: 0,
  y: 0,
  width: 100,
  height: 100,
};

export const MIN_CROP_SIZE = 5;

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

/**
 * CropArea를 항상 유효한 0~100% 범위로 정규화합니다.
 *
 * 이 함수는
 * - ImageCropEditor
 * - CardNewsPreview
 * - LIVE CROP PREVIEW
 *
 * 모두에서 동일하게 사용합니다.
 */
export function normalizeCropArea(cropArea: CropArea): CropArea {
  const width = clamp(
    Number.isFinite(cropArea.width) ? cropArea.width : DEFAULT_CROP_AREA.width,
    MIN_CROP_SIZE,
    100,
  );

  const height = clamp(
    Number.isFinite(cropArea.height)
      ? cropArea.height
      : DEFAULT_CROP_AREA.height,
    MIN_CROP_SIZE,
    100,
  );

  const x = clamp(Number.isFinite(cropArea.x) ? cropArea.x : 0, 0, 100 - width);

  const y = clamp(
    Number.isFinite(cropArea.y) ? cropArea.y : 0,
    0,
    100 - height,
  );

  return {
    x,
    y,
    width,
    height,
  };
}

/**
 * 크롭 영역을 실제 이미지 미리보기에 적용할 CSS를 계산합니다.
 *
 * 중요:
 * 크롭 편집기의 LIVE PREVIEW와
 * 실제 카드뉴스 미리보기에서
 * 반드시 이 함수를 동일하게 사용합니다.
 */
export function getCropImageStyle(
  cropArea: CropArea | null | undefined,
): CSSProperties {
  if (!cropArea) {
    return {};
  }

  const safeCrop = normalizeCropArea(cropArea);

  if (
    safeCrop.x === 0 &&
    safeCrop.y === 0 &&
    safeCrop.width >= 100 &&
    safeCrop.height >= 100
  ) {
    return {};
  }

  const zoom = Math.max(1, 100 / Math.min(safeCrop.width, safeCrop.height));

  const centerX = safeCrop.x + safeCrop.width / 2;
  const centerY = safeCrop.y + safeCrop.height / 2;

  return {
    objectPosition: `${centerX}% ${centerY}%`,
    transform: `scale(${zoom})`,
    transformOrigin: `${centerX}% ${centerY}%`,
  };
}

/**
 * 실제 이미지 크롭 상태가 유효한지 검사합니다.
 */
export function isValidCropArea(
  cropArea: CropArea | null | undefined,
): cropArea is CropArea {
  if (!cropArea) {
    return false;
  }

  return (
    Number.isFinite(cropArea.x) &&
    Number.isFinite(cropArea.y) &&
    Number.isFinite(cropArea.width) &&
    Number.isFinite(cropArea.height) &&
    cropArea.width >= MIN_CROP_SIZE &&
    cropArea.height >= MIN_CROP_SIZE
  );
}
