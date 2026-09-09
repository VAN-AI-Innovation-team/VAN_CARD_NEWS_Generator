import axios from 'axios';

export interface ApprovalRequestListItem {
  approvalRequestId: number;
  contentId: number;
  title: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reason: string | null;
  requestedAt: string;
  processedAt: string | null;
}

export interface ApprovalRequestResponse {
  approvalRequestId: number;
  contentId: number;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reason: string | null;
  requestedAt: string;
  processedAt: string | null;
}

export async function fetchPendingApprovalRequests() {
  const response = await axios.get<ApprovalRequestListItem[]>(
    '/api/approval-requests',
  );
  return response.data;
}

export async function fetchApprovalRequest(approvalRequestId: number) {
  const response = await axios.get<ApprovalRequestResponse>(
    `/api/approval-requests/${approvalRequestId}`,
  );
  return response.data;
}

export async function approveApprovalRequest(approvalRequestId: number) {
  const response = await axios.post<ApprovalRequestResponse>(
    `/api/approval-requests/${approvalRequestId}/approve`,
  );
  return response.data;
}

export async function rejectApprovalRequest(
  approvalRequestId: number,
  reason: string,
) {
  const response = await axios.post<ApprovalRequestResponse>(
    `/api/approval-requests/${approvalRequestId}/reject`,
    { reason },
  );
  return response.data;
}

export async function downloadApprovedCards(contentId: number): Promise<Blob> {
  const response = await axios.get(
    `/api/contents/${contentId}/generated-images/download-all`,
    { responseType: 'blob' },
  );
  return response.data;
}


export interface InstagramPublishResponse {
  publishRecordId: number;
  contentId: number;
  channel: string;
  status: string;
  scheduledAt: string | null;
  igMediaId: string | null;
  permalink: string | null;
  errorMessage: string | null;
  retryCount: number;
  requestedAt: string;
  publishedAt: string | null;
}

/**
 * 서버가 준 사유를 그대로 화면에 올린다. 발행 경로의 400·409는 "왜 막혔는지"가 전부라
 * axios의 기본 문구("Request failed with status code 409")로 덮이면 아무 정보도 남지 않는다.
 */
async function withServerMessage<T>(request: () => Promise<T>): Promise<T> {
  try {
    return await request();
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const message =
        typeof error.response?.data === 'string'
          ? error.response.data
          : error.response?.data?.message;

      if (typeof message === 'string' && message.trim()) {
        throw new Error(message);
      }
    }

    throw error;
  }
}

export async function publishApprovedContentToInstagram(
  contentId: number,
): Promise<InstagramPublishResponse> {
  return withServerMessage(async () => {
    const response = await axios.post<InstagramPublishResponse>(
      `/api/contents/${contentId}/publish/instagram`,
    );
    return response.data;
  });
}

/**
 * 큐에 넣은 건을 서버가 이 요청 안에서 실행하게 한다.
 *
 * 발행 자체가 이 요청 안에서 일어나므로 응답까지 몇 분이 걸린다. 화면은 이 응답을 기다리지 않고
 * 상태 조회로 진행을 본다 — 이 호출의 역할은 "지금 실행해 달라"는 신호뿐이다.
 */
export async function runInstagramPublishNow(
  contentId: number,
): Promise<InstagramPublishResponse> {
  return withServerMessage(async () => {
    const response = await axios.post<InstagramPublishResponse>(
      `/api/contents/${contentId}/publish/instagram/run`,
    );
    return response.data;
  });
}

/**
 * 발행에 나갈 캡션. 미리보기에서 고친 값이 있으면 그것이고, 없으면 서버가 조립한 기본값이다.
 */
export async function fetchInstagramPublishCaption(
  contentId: number,
): Promise<string> {
  const response = await axios.get<{ contentId: number; caption: string }>(
    `/api/contents/${contentId}/publish/instagram/caption`,
  );
  return response.data.caption;
}

export async function saveInstagramPublishCaption(
  contentId: number,
  caption: string,
): Promise<string> {
  return withServerMessage(async () => {
    const response = await axios.put<{ contentId: number; caption: string }>(
      `/api/contents/${contentId}/publish/instagram/caption`,
      { caption },
    );
    return response.data.caption;
  });
}

/**
 * 최신 발행 건의 상태. 발행을 요청한 적이 없으면 404이므로 그때는 null을 준다 —
 * 아직 요청 전이라는 것은 오류가 아니라 정상 상태다.
 */
export async function fetchInstagramPublishStatus(
  contentId: number,
): Promise<InstagramPublishResponse | null> {
  try {
    const response = await axios.get<InstagramPublishResponse>(
      `/api/contents/${contentId}/publish/instagram`,
    );
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      return null;
    }
    throw error;
  }
}
