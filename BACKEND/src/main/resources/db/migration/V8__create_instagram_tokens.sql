-- ==========================================================
-- V8__create_instagram_tokens.sql
--
-- 인스타그램 장기 액세스 토큰을 암호화해 보관합니다.
--
-- configtree(/run/secrets)는 부팅 시 1회 로드라 런타임에 갱신한 토큰을
-- 애플리케이션이 다시 읽지 못합니다. 그래서 토큰만은 DB에 두고,
-- 암호화 키(바뀔 일이 없는 값)만 시크릿으로 주입합니다.
--
-- 팀 계정 1개 고정이므로 실질적으로 1행짜리 테이블입니다.
-- ==========================================================

CREATE TABLE instagram_tokens (
                                  id                     BIGSERIAL PRIMARY KEY,
                                  ig_user_id             VARCHAR(50) NOT NULL UNIQUE,
    -- AES/GCM 암호문(base64: iv || ciphertext). 평문은 어디에도 저장하지 않습니다.
                                  access_token_encrypted TEXT NOT NULL,
    -- 발급(또는 마지막 갱신) 시각. Meta는 발급 24시간이 지나야 갱신을 허용합니다.
                                  issued_at              TIMESTAMPTZ NOT NULL,
                                  expires_at             TIMESTAMPTZ NOT NULL,
                                  last_refreshed_at      TIMESTAMPTZ
);
