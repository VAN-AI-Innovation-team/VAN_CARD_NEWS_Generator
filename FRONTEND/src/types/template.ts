import type { ContentType } from './content';

/**
 * 템플릿 내부 관리 코드
 *
 * 형식: {콘텐츠 유형 접두문자}{일련번호}
 * - A: recruitment, B: event, C: news, D: quote
 * - 일련번호는 같은 접두문자 내에서 1부터 증가합니다.
 * - 예: A1, A2, B1, C3 ...
 *
 * 관리자/개발자/백엔드 식별용이며, 사용자 화면에는 노출하지 않습니다.
 */
export type TemplateCodePrefix = 'A' | 'B' | 'C' | 'D';
export type TemplateCode = `${TemplateCodePrefix}${number}`;

/** 카드뉴스 캔버스 규격 */
export interface CanvasSize {
  width: number;
  height: number;
}

/**
 * 레이아웃 요소에서 공통으로 사용하는 role.
 * 실제 시드 데이터에 등장하는 값을 기준으로 정의합니다.
 */
export type LayoutElementRole =
  | 'image'
  | 'background'
  | 'overlay'
  | 'panel'
  | 'eyebrow'
  | 'title'
  | 'body'
  | 'highlight'
  | 'footer'
  | 'divider'
  | 'decoration'
  | 'badge';

/**
 * layoutDefinition 안의 개별 요소.
 * x/y/width/height는 기존 시드 JSON과 동일하게 캔버스 대비 백분율 기준입니다.
 */
export interface LayoutElement {
  role: LayoutElementRole;
  x: number;
  y: number;
  width: number;
  height: number;
  layer?: number;
  kind?: string;
  text?: string;
  maxChars?: number;
  align?: 'left' | 'center' | 'right';
  colorToken?: string;
  backgroundToken?: string;
  typographyToken?: string;
  cropRatio?: string;
  shape?: string;
}

/**
 * 카드 1장에 대한 레이아웃 정의.
 * cover: 템플릿별 표지 레이아웃
 * content: AI가 본문을 여러 장으로 분할할 때 반복 사용하는 단일 레이아웃
 * closing: 카드 세트 마지막에 사용하는 브랜드 마무리 레이아웃
 */
export interface LayoutCard {
  /** 카드 레이아웃 내부 관리용 유형 코드 */
  type?: string;

  /** 해당 카드 레이아웃의 역할 설명 */
  description?: string;

  elements: Record<string, LayoutElement>;
}

/**
 * 템플릿의 전체 카드 레이아웃.
 * 3분할 표지 옵션은 cards.cover에만 적용하며
 * cards.content / cards.closing에는 적용하지 않습니다.
 */
export interface LayoutDefinition {
  type: string;
  description: string;
  cards: {
    cover: LayoutCard;
    content: LayoutCard;
    closing: LayoutCard;
  };
}

/** 템플릿 디자인 토큰 */
export interface DesignTokens {
  colors: Record<string, string>;
  typography: Record<string, string>;
}

/**
 * 템플릿 데이터
 *
 * 관리자 워크스페이스에서 관리되는 템플릿의
 * 사용자 조회용 API 모델과 대응합니다.
 */
export interface Template {
  id: number;

  /** 내부 관리 코드 A~D */
  code: TemplateCode;

  /** 사용자에게 표시할 템플릿 이름 */
  name: string;

  /** 이 템플릿이 적용 가능한 콘텐츠 유형 */
  contentTypes: ContentType[];

  /** 카드뉴스 캔버스 규격 */
  canvas: CanvasSize;

  /** 카드뉴스 레이아웃 정의 */
  layout: LayoutDefinition;

  /** 색상 / 서체 토큰 */
  designTokens: DesignTokens;

  /** 활성 여부 */
  isActive: boolean;

  /** 템플릿 버전 */
  version: number;
}

/**
 * 사용자 템플릿 조회 응답
 *
 * recommendedTemplateId는 템플릿 자체의 속성이 아니라
 * 현재 조회 조건에서 추천되는 템플릿을 가리키는 값입니다.
 */
export interface TemplateListResponse {
  templates: Template[];
  recommendedTemplateId: number | null;
}
