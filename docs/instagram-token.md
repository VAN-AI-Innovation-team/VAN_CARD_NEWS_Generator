# 인스타그램 액세스 토큰 (저장 · 자동 갱신)

장기 토큰은 **60일 만료**이고, 60일 안에 한 번도 갱신하지 않으면 **영구 만료**된다.
그러면 계정 관리자가 다시 OAuth를 통과해야 하므로 무인 운영이 끊긴다. 그래서 자동 갱신이 필수다.

## 왜 DB에 저장하나

`application.properties`의 `spring.config.import=optional:configtree:/run/secrets/`는
**부팅 시 1회 로드**다. 런타임에 갱신한 토큰을 애플리케이션이 다시 읽지 못하므로,
재시작 전까지 만료된 토큰으로 계속 발행을 시도하게 된다.

그래서 **토큰은 DB에 AES/GCM 암호문으로 저장**하고, **암호화 키만** 시크릿으로 주입한다.
키는 바뀔 일이 없으니 configtree/환경변수로 충분하다.
`javax.crypto`는 자바 표준이라 **추가 의존성이 0**이다.

> 반려안: Secret Manager SDK로 토큰 자체를 관리. 버전 관리·감사 로그가 공짜로 따라오지만
> GCP SDK 의존성이 붙는다. 토큰이 1개(팀 계정 1개 고정)인 규모에는 과하다.
> **팀에 DB 덤프를 외부로 내보내는 관행이 생기면 이 결정을 뒤집을 것.**

## 구성

| 구성요소 | 위치 |
|---|---|
| 테이블 | `instagram_tokens` (`V8__create_instagram_tokens.sql`) |
| 암복호화 | `domain/instagram/service/TokenCipher` — AES/GCM, `base64(iv ‖ ciphertext)` |
| 갱신 API | `global/instagram/InstagramTokenClient` — dev: Mock / prod: `graph.instagram.com` |
| 갱신 로직 | `domain/instagram/service/InstagramTokenService#refresh` |
| 트리거 | `POST /internal/scheduler/refresh-token` ([공유 시크릿 인증](internal-scheduler-trigger.md)) |
| dev 더미 토큰 | `domain/instagram/DevInstagramTokenSeeder` (`@Profile("dev")`) |

규칙 세 가지:

- **발급 24시간 이내엔 갱신하지 않는다.** Meta가 거부한다. 스킵해도 200으로 응답한다.
- **만료 D-14부터 경고 로그**를 남긴다. 스킵 경로에서도 남긴다 — 갱신을 안 하는 동안에도 만료는 다가온다.
- **토큰 평문은 로그·예외 메시지에 싣지 않는다.** 갱신 API가 토큰을 쿼리 파라미터로 받기 때문에
  요청 URI 자체가 비밀이다. `InstagramTokenClientImpl`은 원인 예외를 연결하지 않고 예외 종류와
  상태 코드만 남긴다.

## dev에서 돌려보기

dev 프로필은 기동 시 더미 토큰을 한 번 심는다(발급 시각은 이틀 전). 실 토큰이 필요 없다.

```bash
INTERNAL_SCHEDULER_SECRET=local-dev-secret ./gradlew bootRun

# 갱신 → REFRESHED, 만료일이 60일 뒤로
curl -s -X POST http://localhost:8080/internal/scheduler/refresh-token \
  -H "X-Scheduler-Secret: local-dev-secret"

# 곧바로 다시 호출 → SKIPPED_TOO_EARLY (방금 갱신했으므로 24시간 규칙에 걸린다)
curl -s -X POST http://localhost:8080/internal/scheduler/refresh-token \
  -H "X-Scheduler-Secret: local-dev-secret"
```

만료 경고를 눈으로 보려면 만료일을 당긴 뒤 다시 호출한다.

```sql
UPDATE instagram_tokens SET expires_at = now() + interval '10 days';
```

## 최초 토큰 발급 절차 (실제 발급은 [VAN-18](https://linear.app/ai-agent-study/issue/VAN-18)에서)

계정 관리자 본인이 OAuth를 통과해야 하고, Meta 앱 심사(VAN-1)가 끝나야 하므로
**이 문서는 절차만 적어 둔다.** 목업 개발이 끝나고 프로덕션으로 전환할 때 수행한다.

1. **인가 코드 받기** — 계정 관리자가 브라우저에서 승인하면 `redirect_uri`로 `?code=...`가 붙어 돌아온다.

   ```
   https://www.instagram.com/oauth/authorize
     ?client_id=<IG_APP_ID>
     &redirect_uri=<REDIRECT_URI>
     &scope=instagram_business_basic,instagram_business_content_publish
     &response_type=code
   ```

2. **단기 토큰 교환** (1시간)

   ```bash
   curl -X POST https://api.instagram.com/oauth/access_token \
     -F client_id=<IG_APP_ID> \
     -F client_secret=<IG_APP_SECRET> \
     -F grant_type=authorization_code \
     -F redirect_uri=<REDIRECT_URI> \
     -F code=<CODE>
   # → { "access_token": "...", "user_id": ... }
   ```

3. **장기 토큰 교환** (60일)

   ```bash
   curl -G https://graph.instagram.com/access_token \
     -d grant_type=ig_exchange_token \
     -d client_secret=<IG_APP_SECRET> \
     -d access_token=<SHORT_LIVED_TOKEN>
   # → { "access_token": "...", "expires_in": 5183944 }
   ```

4. **DB에 심기** — 평문을 직접 INSERT하지 말 것. 암호화를 거쳐야 한다.
   전환 시점에 주입 경로(1회용 관리 엔드포인트 또는 부팅 시 시딩)를 VAN-18에서 정한다.

`code`는 1회용이고 몇 분 만에 만료된다. 단기 토큰도 1시간이므로 2~3단계는 이어서 수행한다.

## 운영(GCP) 설정

```bash
# 1. 암호화 키 생성 (base64 32바이트) 후 Secret Manager 등록
openssl rand -base64 32 | tr -d '\n' | \
  gcloud secrets create instagram-token-encryption-key --data-file=-

# 2. Cloud Run에 주입
gcloud run services update van-card-news-backend \
  --region=asia-northeast3 \
  --update-secrets=INSTAGRAM_TOKEN_ENCRYPTION_KEY=instagram-token-encryption-key:latest

# 3. 주 1회 갱신 잡 (재시도는 앱이 아니라 잡 설정이 담당한다)
gcloud scheduler jobs create http instagram-token-refresh \
  --location=asia-northeast3 \
  --schedule="0 4 * * 1" \
  --time-zone=Asia/Seoul \
  --uri=https://<CLOUD_RUN_URL>/internal/scheduler/refresh-token \
  --http-method=POST \
  --headers="X-Scheduler-Secret=<INTERNAL_SCHEDULER_SECRET>" \
  --max-retry-attempts=3
```

- **키를 잃어버리면 저장된 토큰을 복호화할 수 없다.** 그때는 1~4단계를 다시 밟아야 한다.
- **prod 프로필에서 키를 주입하지 않거나 dev 기본 키를 쓰면 기동에 실패한다.** 의도된 동작이다.
  dev 기본 키는 리포에 노출된 값이라 보호 효과가 없다.
- 주 1회면 60일 만료 전에 8번 이상 기회가 있다. 3번 연속 실패해도 D-14 경고가 먼저 뜬다.
