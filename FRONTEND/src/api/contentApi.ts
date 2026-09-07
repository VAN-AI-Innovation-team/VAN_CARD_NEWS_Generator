import axios from 'axios';

export interface CreateContentResponse {
  contentId: number;
  jobHistoryId: number;
  status: string;
  createdAt: string;
}

export interface CreateContentPayload {
  title: string;
  body: string;
  templateId: number;
  images: File[];
}

export interface LayoutElement {
  role: string;
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
  contentField?: 'title' | 'body' | 'highlight' | 'date' | 'location' | 'cta';
}

export interface LayoutCard {
  type?: string;
  description?: string;
  elements: Record<string, LayoutElement>;
}

export interface PreviewTemplate {
  id: number;
  code: string;
  name: string;
  contentType: string;
  version: number;
  canvasWidth: number;
  canvasHeight: number;
  layout: {
    type: string;
    description: string;
    cards: {
      cover: LayoutCard;
      content: LayoutCard;
      closing: LayoutCard;
    };
  };
  designTokens: {
    colors: Record<string, string>;
    typography: Record<string, string>;
  };
}

export interface CropArea {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface CardGenerationResult {
  cover: {
    title: string;
    highlight: string;
    date?: string | null;
    location?: string | null;
    imageId: number | null;
    cropArea: CropArea | null;
  };

  content: Array<{
    title: string;
    body: string;
    highlight: string;
    date?: string | null;
    location?: string | null;
    imageId: number | null;
    cropArea: CropArea | null;
  }>;

  closing: {
    cta: string;
    imageId: number | null;
    cropArea: CropArea | null;
  };
}

export interface PreviewImage {
  id: number;
  imageUrl: string;
  cropArea: string | null;
  generatedImageUrl: string | null;
  sortOrder: number;
}

export interface GeneratedCardImageResponse {
  id: number;
  cardType: 'COVER' | 'CONTENT' | 'CLOSING';
  cardIndex: number;
  sortOrder: number;
  imageUrl: string;
  width: number | null;
  height: number | null;
}

export interface CardImagePlacement {
  cardType: 'cover' | 'content' | 'closing';
  cardIndex: number;
  imageId: number;
  cropArea: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
}

export interface ContentPreviewResponse {
  contentId: number;
  title: string;
  body: string;
  status: string;
  approvalStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | null;
  template: PreviewTemplate;
  cardGenerationResult: CardGenerationResult | null;
  cardImagePlacements: CardImagePlacement[] | null;
  images: PreviewImage[];
  generationStatus:
    'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'IMAGE_PENDING' | null;
}

export async function createContent(
  payload: CreateContentPayload,
): Promise<CreateContentResponse> {
  const formData = new FormData();

  const data = {
    title: payload.title,
    body: payload.body,
    templateId: payload.templateId,
  };

  formData.append(
    'data',
    new Blob([JSON.stringify(data)], {
      type: 'application/json',
    }),
  );

  payload.images.forEach((image) => {
    formData.append('images', image);
  });

  const response = await axios.post<CreateContentResponse>(
    '/api/contents',
    formData,
  );

  return response.data;
}

export async function cloneContent(
  contentId: number,
  regenerate = true,
): Promise<CreateContentResponse> {
  const response = await axios.post<CreateContentResponse>(
    `/api/contents/${contentId}/clone`,
    null,
    {
      params: { regenerate },
    },
  );

  return response.data;
}

export async function fetchContentPreview(
  contentId: number,
): Promise<ContentPreviewResponse> {
  const response = await axios.get<ContentPreviewResponse>(
    `/api/contents/${contentId}/preview`,
  );

  return response.data;
}

export interface EditContentPayload {
  title: string;
  body: string;
  templateId: number;
  keepImageIds: number[];
  images: File[];
}

export async function editContent(
  contentId: number,
  payload: EditContentPayload,
): Promise<CreateContentResponse> {
  const formData = new FormData();

  formData.append(
    'data',
    new Blob(
      [
        JSON.stringify({
          title: payload.title,
          body: payload.body,
          templateId: payload.templateId,
          keepImageIds: payload.keepImageIds,
        }),
      ],
      {
        type: 'application/json',
      },
    ),
  );

  payload.images.forEach((image) => {
    formData.append('images', image);
  });

  const response = await axios.put<CreateContentResponse>(
    `/api/contents/${contentId}/edit`,
    formData,
  );

  return response.data;
}

export type HighlightCardType = 'COVER' | 'CONTENT';

export async function updateContentTemplate(
  contentId: number,
  templateId: number,
): Promise<ContentPreviewResponse> {
  const response = await axios.put<ContentPreviewResponse>(
    `/api/contents/${contentId}/template`,
    { templateId },
  );

  return response.data;
}

export interface CardRegenerationPayload {
  cardType: 'cover' | 'content' | 'closing';
  cardIndex: number;
  instruction: string;
}

export async function regenerateCard(
  contentId: number,
  payload: CardRegenerationPayload,
): Promise<ContentPreviewResponse> {
  const response = await axios.post<ContentPreviewResponse>(
    `/api/contents/${contentId}/cards/regenerate`,
    payload,
  );

  return response.data;
}

export interface HighlightUpdatePayload {
  cardType: HighlightCardType;
  cardIndex: number;
  highlight: string;
}

export async function updateCardHighlight(
  contentId: number,
  payload: HighlightUpdatePayload,
): Promise<ContentPreviewResponse> {
  const response = await axios.put<ContentPreviewResponse>(
    `/api/contents/${contentId}/highlight`,
    payload,
  );

  return response.data;
}

/**
 * 조사 '이/가'를 문법에 맞게 선택합니다.
 */
function getSubjectParticle(word: string): '이' | '가' {
  if (!word) {
    return '이';
  }

  const lastChar = word.charCodeAt(word.length - 1);

  if (lastChar >= 0xac00 && lastChar <= 0xd7a3) {
    return (lastChar - 0xac00) % 28 === 0 ? '가' : '이';
  }

  return '가';
}

/**
 * 백엔드 validation 에러를
 * 카드 번호 기준 메시지로 변환합니다.
 */
function formatContentValidationError(message: string): string {
  const contentFieldMatch = message.match(
    /content\[(\d+)\]\.(title|body|highlight|date|location|cta)/,
  );

  if (contentFieldMatch) {
    const contentIndex = Number(contentFieldMatch[1]);

    const field = contentFieldMatch[2];

    const cardNumber = contentIndex + 2;

    const fieldNames: Record<string, string> = {
      title: '제목',
      body: '본문',
      highlight: '강조 문구',
      date: '날짜',
      location: '장소',
      cta: 'CTA',
    };

    const fieldName = fieldNames[field] ?? field;

    const particle = getSubjectParticle(fieldName);

    return `${cardNumber}번 카드의 ${fieldName}${particle} 비어 있습니다. 내용을 입력해주세요.`;
  }

  const coverFieldMatch = message.match(
    /cover\.(title|body|highlight|date|location|cta)/,
  );

  if (coverFieldMatch) {
    const field = coverFieldMatch[1];

    const fieldNames: Record<string, string> = {
      title: '제목',
      body: '본문',
      highlight: '강조 문구',
      date: '날짜',
      location: '장소',
      cta: 'CTA',
    };

    const fieldName = fieldNames[field] ?? field;

    const particle = getSubjectParticle(fieldName);

    return `1번 카드의 ${fieldName}${particle} 비어 있습니다. 내용을 입력해주세요.`;
  }

  const closingFieldMatch = message.match(
    /closing\.(title|body|highlight|date|location|cta)/,
  );

  if (closingFieldMatch) {
    const field = closingFieldMatch[1];

    const fieldNames: Record<string, string> = {
      title: '제목',
      body: '본문',
      highlight: '강조 문구',
      date: '날짜',
      location: '장소',
      cta: 'CTA',
    };

    const fieldName = fieldNames[field] ?? field;

    const particle = getSubjectParticle(fieldName);

    return `마지막 카드의 ${fieldName}${particle} 비어 있습니다. 내용을 입력해주세요.`;
  }

  return message;
}

export async function updateContentPreview(
  contentId: number,
  cardGenerationResult: CardGenerationResult,
): Promise<ContentPreviewResponse> {
  try {
    const response = await axios.put<ContentPreviewResponse>(
      `/api/contents/${contentId}/preview`,
      {
        cardGenerationResult,
      },
    );

    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const serverMessage =
        typeof error.response?.data === 'string'
          ? error.response.data
          : error.response?.data?.message;

      if (typeof serverMessage === 'string' && serverMessage.trim()) {
        throw new Error(formatContentValidationError(serverMessage));
      }
    }

    throw error;
  }
}

export async function updateCardImagePlacements(
  contentId: number,
  placements: CardImagePlacement[],
): Promise<ContentPreviewResponse> {
  const response = await axios.put<ContentPreviewResponse>(
    `/api/contents/${contentId}/images/crop`,
    {
      placements,
    },
  );

  return response.data;
}

export async function generateCardImages(
  contentId: number,
): Promise<GeneratedCardImageResponse[]> {
  const response = await axios.post<GeneratedCardImageResponse[]>(
    `/api/contents/${contentId}/generated-images/generate`,
  );

  return response.data;
}

export async function fetchGeneratedCardImages(
  contentId: number,
): Promise<GeneratedCardImageResponse[]> {
  const response = await axios.get<GeneratedCardImageResponse[]>(
    `/api/contents/${contentId}/generated-images`,
  );

  return response.data;
}

export async function requestContentApproval(
  contentId: number,
): Promise<ApprovalRequestResponse> {
  const response = await axios.post<ApprovalRequestResponse>(
    `/api/contents/${contentId}/approval-requests`,
  );

  return response.data;
}

export interface ApprovalRequestResponse {
  approvalRequestId: number;
  contentId: number;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  requestedAt: string;
}

export interface ContentManagementListItem {
  contentId: number;
  title: string;
  createdAt: string;
  cardCount: number;
  contentStatus: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  approvalStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | null;
  approvalRequestId: number | null;
  generationStatus:
    'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'IMAGE_PENDING' | null;
}

export interface ContentHistoryPageResponse {
  contents: ContentManagementListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export async function fetchContentManagementList(
  page = 0,
  size = 20,
): Promise<ContentHistoryPageResponse> {
  const response = await axios.get<ContentHistoryPageResponse>(
    '/api/contents/history',
    {
      params: { page, size },
    },
  );

  return response.data;
}
