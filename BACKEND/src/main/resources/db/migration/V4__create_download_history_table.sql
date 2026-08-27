-- ==========================================================
-- V4__create_download_history_table.sql
-- 승인된 콘텐츠 다운로드 시도 이력
-- ==========================================================

CREATE TABLE download_histories (
                                    id              BIGSERIAL PRIMARY KEY,
                                    content_id      BIGINT NOT NULL,
                                    channel         VARCHAR(50) NOT NULL,
                                    download_type   VARCHAR(20) NOT NULL
                                        CHECK (download_type IN ('SINGLE', 'ZIP')),
                                    result          VARCHAR(20) NOT NULL
                                        CHECK (result IN ('PENDING', 'SUCCESS', 'FAILED')),
                                    retry_count     INT NOT NULL DEFAULT 0
                                        CHECK (retry_count >= 0),
                                    image_id        BIGINT,
                                    error_message   TEXT,
                                    requested_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    completed_at    TIMESTAMPTZ,

                                    CONSTRAINT fk_download_histories_content
                                        FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE RESTRICT
);

CREATE INDEX idx_download_histories_content_id
    ON download_histories(content_id);

CREATE INDEX idx_download_histories_content_channel
    ON download_histories(content_id, channel);

CREATE INDEX idx_download_histories_requested_at
    ON download_histories(requested_at DESC);

CREATE INDEX idx_download_histories_retry
    ON download_histories(content_id, channel, download_type, retry_count);
