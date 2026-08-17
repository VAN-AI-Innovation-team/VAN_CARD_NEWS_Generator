CREATE TABLE contents (
                          id          BIGSERIAL PRIMARY KEY,
                          title       VARCHAR(200) NOT NULL,
                          body        TEXT NOT NULL,
                          status      VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                              CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
                          template    VARCHAR(100),
                          created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_contents_status ON contents (status);

CREATE TABLE content_images (
                                id          BIGSERIAL PRIMARY KEY,
                                content_id  BIGINT NOT NULL REFERENCES contents (id) ON DELETE CASCADE,
                                image_url   VARCHAR(500) NOT NULL,
                                sort_order  INT NOT NULL DEFAULT 0
                                    CHECK (sort_order >= 0 AND sort_order < 10)
);

CREATE INDEX idx_content_images_content_id ON content_images (content_id);
