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
