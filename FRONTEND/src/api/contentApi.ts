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
    imageId: number | null;
    cropArea: CropArea | null;
  };

  content: Array<{
    title: string;
    body: string;
    highlight: string;
    imageId: number | null;
    cropArea: CropArea | null;
  }>;

  closing: {
    cta: string;
  };
}

export interface PreviewImage {
  id: number;
  imageUrl: string;
  cropArea: string | null;
  sortOrder: number;
}

export interface ContentPreviewResponse {
  contentId: number;
  title: string;
  body: string;
  status: string;
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
