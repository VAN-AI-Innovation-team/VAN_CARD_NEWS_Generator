import type { ContentType } from './content';

/**
 * 템플릿 내부 관리 코드
 *
 * A~D는 관리자/개발자/백엔드 식별용입니다.
 * 사용자 화면에는 노출하지 않습니다.
 */
export type TemplateCode = 'A' | 'B' | 'C' | 'D';

/**
 * 카드뉴스 캔버스 규격
 */
export interface CanvasSize {
  width: number;
  height: number;
}

/**
 * 템플릿 레이아웃 정의
 *
 * 실제 레이아웃 JSON 구조가 확정되면
 * 세부 필드를 추가할 수 있습니다.
 */
export interface LayoutDefinition {
  type: string;
  description: string;
  elements?: Record<string, unknown>;
}

/**
 * 템플릿 디자인 토큰
 */
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

  /**
   * 내부 관리 코드 A~D
   */
  code: TemplateCode;

  /**
   * 사용자에게 표시할 템플릿 이름
   */
  name: string;

  /**
   * 이 템플릿이 적용 가능한 콘텐츠 유형
   */
  contentTypes: ContentType[];

  /**
   * 카드뉴스 캔버스 규격
   */
  canvas: CanvasSize;

  /**
   * 카드뉴스 레이아웃 정의
   */
  layout: LayoutDefinition;

  /**
   * 색상 / 서체 토큰
   */
  designTokens: DesignTokens;

  /**
   * 활성 여부
   */
  isActive: boolean;

  /**
   * 템플릿 버전
   */
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
