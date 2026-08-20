-- ==========================================================
-- V1__init.sql: 전체 스키마 통합 초기화
-- ==========================================================

-- 1. Templates 테이블[cite: 3]
CREATE TABLE templates (
                           id                  BIGSERIAL PRIMARY KEY,
                           code                VARCHAR(30) NOT NULL,
                           name                VARCHAR(100) NOT NULL,
                           content_type        VARCHAR(30) NOT NULL
                               CHECK (content_type IN ('recruitment', 'event', 'news', 'quote')),
                           canvas_width        INT NOT NULL CHECK (canvas_width > 0),
                           canvas_height       INT NOT NULL CHECK (canvas_height > 0),
                           layout_definition   JSONB NOT NULL,
                           design_tokens       JSONB NOT NULL,
                           is_active           BOOLEAN NOT NULL DEFAULT TRUE,
                           version             INT NOT NULL CHECK (version > 0),
                           created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT uk_templates_code_version UNIQUE (code, version)
);

CREATE INDEX idx_templates_content_type ON templates (content_type);
CREATE INDEX idx_templates_active ON templates (is_active);
CREATE INDEX idx_templates_code ON templates (code);
CREATE INDEX idx_templates_content_type_active ON templates (content_type, is_active);


-- 2. Contents 테이블 (template_id, card_generation_result, card_image_placements 통합)[cite: 1, 4, 5, 8, 10]
CREATE TABLE contents (
                          id                      BIGSERIAL PRIMARY KEY,
                          title                   VARCHAR(200) NOT NULL,
                          body                    TEXT NOT NULL,
                          status                  VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                              CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
                          template_id             BIGINT NOT NULL,
                          card_generation_result  JSONB,
                          card_image_placements   JSONB,
                          created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT fk_contents_template
                              FOREIGN KEY (template_id) REFERENCES templates(id) ON DELETE RESTRICT
);

CREATE INDEX idx_contents_status ON contents (status);
CREATE INDEX idx_contents_template_id ON contents (template_id);


-- 3. Content Images 테이블 (메타데이터 컬럼 통합)[cite: 1, 5]
CREATE TABLE content_images (
                                id                    BIGSERIAL PRIMARY KEY,
                                content_id            BIGINT NOT NULL REFERENCES contents (id) ON DELETE CASCADE,
                                image_url             VARCHAR(500) NOT NULL,
                                sort_order            INT NOT NULL DEFAULT 0 CHECK (sort_order >= 0 AND sort_order < 10),
                                crop_area             TEXT,
                                generated_image_url   VARCHAR(500),
                                resolution_width      INT,
                                resolution_height     INT,
                                storage_ref           VARCHAR(500)
);

CREATE INDEX idx_content_images_content_id ON content_images (content_id);


-- 4. Job Histories 테이블[cite: 2]
CREATE TABLE job_histories (
                               id            BIGSERIAL PRIMARY KEY,
                               content_id    BIGINT NOT NULL,
                               job_type      VARCHAR(30) NOT NULL,
                               status        VARCHAR(30) NOT NULL
                                   CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
                               result_url    TEXT,
                               error_message TEXT,
                               requested_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               completed_at  TIMESTAMPTZ,
                               CONSTRAINT fk_job_histories_content
                                   FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE RESTRICT
);

ALTER TABLE job_histories
    ADD CONSTRAINT chk_job_histories_job_type
        CHECK (job_type IN ('COPY_GENERATION', 'IMAGE_GENERATION', 'FULL_PIPELINE'));

CREATE INDEX idx_job_histories_content_id ON job_histories(content_id);
CREATE INDEX idx_job_histories_status ON job_histories(status);


-- 5. Approval Requests 테이블[cite: 6]
CREATE TABLE approval_requests (
                                   id              BIGSERIAL PRIMARY KEY,
                                   content_id      BIGINT NOT NULL,
                                   requester_id    VARCHAR(100),
                                   approver_id     VARCHAR(100),
                                   status          VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
                                   reason          TEXT,
                                   requested_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   processed_at    TIMESTAMPTZ,
                                   CONSTRAINT fk_approval_requests_content
                                       FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE RESTRICT
);

CREATE INDEX idx_approval_requests_content_id ON approval_requests(content_id);
CREATE INDEX idx_approval_requests_status ON approval_requests(status);
CREATE INDEX idx_approval_requests_content_status ON approval_requests(content_id, status);


-- 6. Publish Records 테이블[cite: 7]
CREATE TABLE publish_records (
                                 id                  BIGSERIAL PRIMARY KEY,
                                 content_id          BIGINT NOT NULL,
                                 channel             VARCHAR(50) NOT NULL,
                                 external_post_id    VARCHAR(200),
                                 published_at        TIMESTAMPTZ,
                                 result              VARCHAR(20) NOT NULL CHECK (result IN ('PENDING', 'SUCCESS', 'FAILED')),
                                 error_message       TEXT,
                                 retry_count         INT NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
                                 CONSTRAINT fk_publish_records_content
                                     FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE RESTRICT
);

CREATE INDEX idx_publish_records_content_id ON publish_records(content_id);
CREATE INDEX idx_publish_records_result ON publish_records(result);
CREATE INDEX idx_publish_records_content_channel ON publish_records(content_id, channel);


-- 7. Generated Card Images 테이블
CREATE TABLE generated_card_images (
                                       id                   BIGSERIAL PRIMARY KEY,
                                       content_id           BIGINT NOT NULL,
                                       card_type            VARCHAR(20) NOT NULL,
                                       card_index           INT NOT NULL,
                                       column_index         INT NOT NULL,
                                       sort_order           INT NOT NULL,
                                       image_url            VARCHAR(500) NOT NULL,
                                       resolution_width     INT,
                                       resolution_height    INT,
                                       storage_ref          VARCHAR(500),
                                       created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
                                       CONSTRAINT fk_generated_card_images_content
                                           FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE,
                                       CONSTRAINT chk_generated_card_images_card_type
                                           CHECK (card_type IN ('COVER', 'CONTENT', 'CLOSING'))
);

CREATE INDEX idx_generated_card_images_content_id ON generated_card_images (content_id);
