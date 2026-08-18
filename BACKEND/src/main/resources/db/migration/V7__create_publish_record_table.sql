CREATE TABLE publish_records (
                                 id                  BIGSERIAL PRIMARY KEY,

                                 content_id          BIGINT NOT NULL,

                                 channel             VARCHAR(50) NOT NULL,

                                 external_post_id    VARCHAR(200),

                                 published_at        TIMESTAMPTZ,

                                 result              VARCHAR(20) NOT NULL
                                     CHECK (result IN ('PENDING', 'SUCCESS', 'FAILED')),

                                 error_message       TEXT,

                                 retry_count         INT NOT NULL DEFAULT 0
                                     CHECK (retry_count >= 0),

                                 CONSTRAINT fk_publish_records_content
                                     FOREIGN KEY (content_id)
                                         REFERENCES contents(id)
                                         ON DELETE RESTRICT
);

CREATE INDEX idx_publish_records_content_id
    ON publish_records(content_id);

CREATE INDEX idx_publish_records_result
    ON publish_records(result);

CREATE INDEX idx_publish_records_content_channel
    ON publish_records(content_id, channel);
