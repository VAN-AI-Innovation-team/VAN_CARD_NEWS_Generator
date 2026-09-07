-- ==========================================================
-- V5__create_audit_logs_table.sql
-- 콘텐츠 생성/수정/승인/반려/다운로드 행위 감사 로그
-- ==========================================================

CREATE TABLE audit_logs (
                            id          BIGSERIAL PRIMARY KEY,
                            actor_id    VARCHAR(100) NOT NULL,
                            action      VARCHAR(30) NOT NULL
                                CHECK (action IN ('CREATE', 'UPDATE', 'APPROVAL_REQUEST', 'APPROVE', 'REJECT', 'DOWNLOAD')),
                           target_type VARCHAR(30) NOT NULL
                               CHECK (target_type IN ('CONTENT')),
                           target_id   BIGINT NOT NULL,
                           occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           detail      TEXT,
                           CONSTRAINT fk_audit_logs_content
                               FOREIGN KEY (target_id) REFERENCES contents(id) ON DELETE RESTRICT
);

CREATE INDEX idx_audit_logs_target_id
    ON audit_logs(target_id);

CREATE INDEX idx_audit_logs_occurred_at
    ON audit_logs(occurred_at DESC);

CREATE INDEX idx_audit_logs_actor_id
    ON audit_logs(actor_id);

CREATE INDEX idx_audit_logs_action
    ON audit_logs(action);
