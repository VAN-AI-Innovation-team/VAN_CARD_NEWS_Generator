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
  /** 템플릿이 실제 카드 데이터에서 값을 가져올 필드입니다. */
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

export interface ContentPreviewResponse {
  contentId: number;
  title: string;
  body: string;
  status: string;
  approvalStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | null;
  template: PreviewTemplate;
  cardGenerationResult: CardGenerationResult | null;
  images: PreviewImage[];
}

export interface CropArea {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface ImageCropUpdate {
  imageId: number;
  cropArea: CropArea;
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
      { type: 'application/json' },
    ),
  );
  payload.images.forEach((image) => formData.append('images', image));

  const response = await axios.put<CreateContentResponse>(
    `/api/contents/${contentId}/edit`,
    formData,
  );
  return response.data;
}

export type HighlightCardType = 'COVER' | 'CONTENT';

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

export async function updateContentPreview(
  contentId: number,
  cardGenerationResult: CardGenerationResult,
): Promise<ContentPreviewResponse> {
  const response = await axios.put<ContentPreviewResponse>(
    `/api/contents/${contentId}/preview`,
    {
      cardGenerationResult,
    },
  );

  return response.data;
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
}

export async function fetchContentManagementList(): Promise<
  ContentManagementListItem[]
> {
  const response =
    await axios.get<ContentManagementListItem[]>('/api/contents');
  return response.data;
}
