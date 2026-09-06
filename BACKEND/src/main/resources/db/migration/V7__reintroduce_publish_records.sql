-- ==========================================================
-- V7__reintroduce_publish_records.sql
--
-- V6에서 드롭한 publish_records를 인스타 발행 스키마로 재도입합니다.
--
-- Meta가 예약 발행을 지원하지 않으므로 이 테이블이 예약 큐를 겸합니다.
-- 즉시 발행도 scheduled_at = now()로 이 테이블을 거칩니다.
-- ==========================================================

CREATE TABLE publish_records (
                                 id                    BIGSERIAL PRIMARY KEY,
                                 content_id            BIGINT NOT NULL,
                                 channel               VARCHAR(50) NOT NULL,
                                 ig_media_id           VARCHAR(100),
                                 permalink             TEXT,
                                 status                VARCHAR(20) NOT NULL
                                     CHECK (status IN ('SCHEDULED', 'PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'CANCELED')),
                                 scheduled_at          TIMESTAMPTZ,
                                 processing_started_at TIMESTAMPTZ,
                                 caption               TEXT,
                                 error_message         TEXT,
                                 retry_count           INT NOT NULL DEFAULT 0
                                     CHECK (retry_count >= 0),
                                 requested_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 published_at          TIMESTAMPTZ,

                                 CONSTRAINT fk_publish_records_content
                                     FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE RESTRICT
);

-- 같은 콘텐츠를 같은 채널에 두 번 성공 발행하는 것을 차단합니다.
-- 실패/취소 이력은 여러 건 존재할 수 있습니다. (V3의 중복 승인요청 방지와 같은 패턴)
CREATE UNIQUE INDEX uk_publish_records_content_channel_success
    ON publish_records (content_id, channel)
    WHERE status = 'SUCCESS';

-- 워커가 실행 대상을 긁어오는 조회 경로입니다.
CREATE INDEX idx_publish_records_status_scheduled_at
    ON publish_records (status, scheduled_at);

CREATE INDEX idx_publish_records_content_id
    ON publish_records (content_id);

-- ==========================================================
-- contents.status = PUBLISHED의 의미를 실제 외부 발행 전용으로 축소합니다.
--
-- 지금까지 content.publish()를 호출하는 곳은 다운로드 성공 처리 한 곳뿐이었으므로,
-- 현재 PUBLISHED인 행은 전부 다운로드 기인입니다. 실제 인스타 발행은 아직 없습니다.
-- 따라서 조건 없는 일괄 복원이 오탐 없이 정확합니다.
-- ==========================================================
UPDATE contents
SET status = 'DRAFT'
WHERE status = 'PUBLISHED';
