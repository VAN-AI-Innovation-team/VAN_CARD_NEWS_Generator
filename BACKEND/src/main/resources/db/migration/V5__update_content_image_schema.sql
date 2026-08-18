-- V1에서 생성된 사용하지 않는 template 컬럼 제거
ALTER TABLE contents
DROP COLUMN IF EXISTS template;

-- 현재 Content Entity에서는 template_id가 필수이므로 NOT NULL로 변경
ALTER TABLE contents
    ALTER COLUMN template_id SET NOT NULL;

-- PDF Asset 요구사항에 대응하는 이미지 메타데이터 추가
ALTER TABLE content_images
    ADD COLUMN crop_area TEXT;

ALTER TABLE content_images
    ADD COLUMN generated_image_url VARCHAR(500);

ALTER TABLE content_images
    ADD COLUMN resolution_width INT;

ALTER TABLE content_images
    ADD COLUMN resolution_height INT;

ALTER TABLE content_images
    ADD COLUMN storage_ref VARCHAR(500);
