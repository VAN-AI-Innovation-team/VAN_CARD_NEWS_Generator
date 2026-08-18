CREATE TABLE templates (
                           id                  BIGSERIAL PRIMARY KEY,

                           code                VARCHAR(30) NOT NULL,

                           name                VARCHAR(100) NOT NULL,

                           content_type        VARCHAR(30) NOT NULL
                               CHECK (
                                   content_type IN (
                                                    'recruitment',
                                                    'event',
                                                    'news',
                                                    'quote'
                                       )
                                   ),

                           canvas_width        INT NOT NULL
                               CHECK (canvas_width > 0),

                           canvas_height       INT NOT NULL
                               CHECK (canvas_height > 0),

                           layout_definition   JSONB NOT NULL,

                           design_tokens       JSONB NOT NULL,

                           is_active           BOOLEAN NOT NULL DEFAULT TRUE,

                           version             INT NOT NULL
                               CHECK (version > 0),

                           created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                           updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                           CONSTRAINT uk_templates_code_version
                               UNIQUE (code, version)
);

CREATE INDEX idx_templates_content_type
    ON templates (content_type);

CREATE INDEX idx_templates_active
    ON templates (is_active);

CREATE INDEX idx_templates_code
    ON templates (code);

CREATE INDEX idx_templates_content_type_active
    ON templates (content_type, is_active);
