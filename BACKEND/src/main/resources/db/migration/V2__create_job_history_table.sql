CREATE TABLE job_histories (
                               id           BIGSERIAL PRIMARY KEY,
                               content_id   BIGINT NOT NULL,
                               job_type     VARCHAR(30) NOT NULL,
                               status       VARCHAR(30) NOT NULL
                                   CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
                               result_url   TEXT,
                               error_message TEXT,
                               requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               completed_at TIMESTAMPTZ,
                               CONSTRAINT fk_job_histories_content
                                   FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE RESTRICT
);

ALTER TABLE job_histories
    ADD CONSTRAINT chk_job_histories_job_type
        CHECK (job_type IN ('COPY_GENERATION', 'IMAGE_GENERATION', 'FULL_PIPELINE'));

CREATE INDEX idx_job_histories_content_id ON job_histories(content_id);
CREATE INDEX idx_job_histories_status ON job_histories(status);
