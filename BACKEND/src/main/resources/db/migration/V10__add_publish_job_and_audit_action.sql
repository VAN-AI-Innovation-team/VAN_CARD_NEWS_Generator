-- ==========================================================
-- V10__add_publish_job_and_audit_action.sql
--
-- 인스타그램 발행을 기존 이력 체계에 남기기 위해 두 CHECK 제약을 재정의합니다.
-- 두 컬럼 모두 CHECK가 걸려 있어 애플리케이션 enum에 값을 더하는 것만으로는 INSERT가 거절됩니다.
--
-- audit_logs.action의 제약은 V5에서 컬럼 인라인으로 선언되어 Postgres가 이름을 지었습니다
-- (audit_logs_action_check). 이름을 못 맞추면 이 마이그레이션이 실패하도록 IF EXISTS를 쓰지 않습니다.
-- 조용히 건너뛰면 옛 제약이 남아 발행 시점에야 드러납니다.
-- ==========================================================

ALTER TABLE job_histories
    DROP CONSTRAINT chk_job_histories_job_type;

ALTER TABLE job_histories
    ADD CONSTRAINT chk_job_histories_job_type
        CHECK (job_type IN ('COPY_GENERATION', 'IMAGE_GENERATION', 'FULL_PIPELINE', 'INSTAGRAM_PUBLISH'));

ALTER TABLE audit_logs
    DROP CONSTRAINT audit_logs_action_check;

ALTER TABLE audit_logs
    ADD CONSTRAINT audit_logs_action_check
        CHECK (action IN ('CREATE', 'UPDATE', 'APPROVAL_REQUEST', 'APPROVE', 'REJECT', 'DOWNLOAD', 'PUBLISH'));
