import axios from 'axios';

export interface PublishRecordResponse {
  publishRecordId: number;
  contentId: number;
  channel: string;
  status: 'SCHEDULED' | 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'CANCELED';
  scheduledAt: string;
  igMediaId: string | null;
  permalink: string | null;
  errorMessage: string | null;
  retryCount: number;
  requestedAt: string;
  publishedAt: string | null;
}

export async function publishToInstagram(
  contentId: number,
  caption?: string,
): Promise<PublishRecordResponse> {
  const response = await axios.post<PublishRecordResponse>(
    `/api/contents/${contentId}/publish/instagram`,
    caption ? { caption } : undefined,
  );

  return response.data;
}

export async function fetchInstagramPublishStatus(
  contentId: number,
): Promise<PublishRecordResponse> {
  const response = await axios.get<PublishRecordResponse>(
    `/api/contents/${contentId}/publish/instagram`,
  );

  return response.data;
}
