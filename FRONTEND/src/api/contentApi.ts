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

/**
 * 카드뉴스 생성 요청
 *
 * Backend ContentController는
 * multipart/form-data 형식으로
 *
 * data   : application/json
 * images : MultipartFile[]
 *
 * 를 받습니다.
 */
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
    new Blob([JSON.stringify(data)], { type: 'application/json' }),
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
