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
      const data = error.response?.data;
      // 서버가 JSON 오류를 못 만들고 컨테이너가 끼어들면 본문은 HTML 오류 페이지다.
      // 그것을 사유로 올리면 화면에 페이지 원문이 통째로 찍혀 아무것도 읽히지 않는다.
      const message =
        typeof data === 'string'
          ? data.trimStart().startsWith('<')
            ? null
            : data
          : data?.message;

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
 * 예약 발행 등록. 시각은 Asia/Seoul 기준의 로컬 시각 문자열(YYYY-MM-DDTHH:mm)이다 —
 * 서버가 LocalDateTime으로 받으므로 타임존을 붙이면 안 된다.
 */
export async function scheduleInstagramPublish(
  contentId: number,
  scheduledAt: string,
  caption: string,
): Promise<InstagramPublishResponse> {
  return withServerMessage(async () => {
    const response = await axios.post<InstagramPublishResponse>(
      `/api/contents/${contentId}/publish/instagram/schedule`,
      { scheduledAt, caption },
    );
    return response.data;
  });
}

/** 예약 취소. 워커가 이미 집어간 건은 409로 막힌다. */
export async function cancelInstagramSchedule(
  contentId: number,
): Promise<InstagramPublishResponse> {
  return withServerMessage(async () => {
    const response = await axios.delete<InstagramPublishResponse>(
      `/api/contents/${contentId}/publish/instagram/schedule`,
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
