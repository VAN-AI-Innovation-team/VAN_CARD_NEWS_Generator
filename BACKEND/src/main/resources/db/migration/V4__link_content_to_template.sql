ALTER TABLE contents
    ADD COLUMN template_id BIGINT;

ALTER TABLE contents
    ADD CONSTRAINT fk_contents_template
        FOREIGN KEY (template_id)
            REFERENCES templates(id)
            ON DELETE RESTRICT;

CREATE INDEX idx_contents_template_id
    ON contents(template_id);
