-- ==========================================================
-- V3__prevent_duplicate_pending_approval_requests.sql
--
-- 동일 콘텐츠에 대해 PENDING 승인 요청이
-- 동시에 여러 개 생성되는 것을 DB 수준에서 방지합니다.
--
-- APPROVED / REJECTED 이력은 여러 건 존재할 수 있지만,
-- PENDING은 콘텐츠당 최대 1건만 존재할 수 있습니다.
-- ==========================================================

CREATE UNIQUE INDEX uk_approval_requests_content_pending
    ON approval_requests (content_id)
    WHERE status = 'PENDING';
