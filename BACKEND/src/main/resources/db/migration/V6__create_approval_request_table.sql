CREATE TABLE approval_requests (
                                   id              BIGSERIAL PRIMARY KEY,

                                   content_id      BIGINT NOT NULL,

                                   requester_id    VARCHAR(100),

                                   approver_id     VARCHAR(100),

                                   status          VARCHAR(20) NOT NULL
                                       CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),

                                   reason          TEXT,

                                   requested_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   processed_at    TIMESTAMPTZ,

                                   CONSTRAINT fk_approval_requests_content
                                       FOREIGN KEY (content_id)
                                           REFERENCES contents(id)
                                           ON DELETE RESTRICT
);

CREATE INDEX idx_approval_requests_content_id
    ON approval_requests(content_id);

CREATE INDEX idx_approval_requests_status
    ON approval_requests(status);

CREATE INDEX idx_approval_requests_content_status
    ON approval_requests(content_id, status);
