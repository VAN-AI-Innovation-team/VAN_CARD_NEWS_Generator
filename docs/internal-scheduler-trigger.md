# 내부 스케줄러 트리거 (`/internal/**`)

Cloud Run은 유휴 시 인스턴스를 0으로 줄이고, 요청을 처리 중이 아닐 때는 CPU를 할당하지 않는다.
그래서 `@Scheduled`(주기 실행)도 `@Async`(응답 후 백그라운드)도 신뢰할 수 없다.
대신 **Cloud Scheduler가 HTTP로 두드려서** 요청 컨텍스트 안에서 작업을 끝까지 수행한다.

이 문서는 그 트리거 엔드포인트를 보호하는 **공유 시크릿 인증**을 다룬다.
트리거 엔드포인트 자체(`publish-due`, `refresh-token`)는 각 기능 이슈에서 추가한다.

## 동작

`/internal/**` 로 오는 모든 요청은 `X-Scheduler-Secret` 헤더가
`app.internal.scheduler-secret`(환경변수 `INTERNAL_SCHEDULER_SECRET`)과
정확히 일치해야 통과한다. 그 외에는 **403**.

- 비교는 `MessageDigest.isEqual()` 상수시간 비교 — 앞자리부터 비교하다 멈추면 응답 시간 차이로 값을 추측당한다.
- **시크릿이 비어 있으면 전면 거부**한다. 환경변수를 깜빡한 배포가 "헤더 없이 다 통과"로 열리지 않게 하기 위함이다.

구현: `global/config/WebConfig#hasSchedulerSecret`, 검증: `InternalEndpointSecurityTest`.

## 로컬에서 호출하기

```bash
# 1. 시크릿을 넣고 기동
INTERNAL_SCHEDULER_SECRET=local-dev-secret ./gradlew bootRun

# 2. 호출 (엔드포인트 경로는 기능 이슈에서 추가되는 대로)
curl -i -X POST http://localhost:8080/internal/scheduler/publish-due \
  -H "X-Scheduler-Secret: local-dev-secret"

# 3. 거부되는지 확인
curl -i -X POST http://localhost:8080/internal/scheduler/publish-due          # 403
curl -i -X POST http://localhost:8080/internal/scheduler/publish-due \
  -H "X-Scheduler-Secret: wrong"                                             # 403
```

`INTERNAL_SCHEDULER_SECRET` 없이 띄우면 맞는 헤더를 보내도 403이다. 위 1번을 빼먹지 말 것.

## 운영(GCP) 설정

시크릿은 Secret Manager에 두고 Cloud Run에 주입한다.

```bash
# 랜덤 시크릿 생성 후 등록
openssl rand -base64 48 | tr -d '\n' | \
  gcloud secrets create internal-scheduler-secret --data-file=-

# Cloud Run에 환경변수로 주입
gcloud run services update van-card-news-backend \
  --region=asia-northeast3 \
  --update-secrets=INTERNAL_SCHEDULER_SECRET=internal-scheduler-secret:latest
```

잡을 만들 때 헤더로 같은 시크릿을 실어 보내고, **재시도는 앱 코드가 아니라 잡의 재시도 설정**을 쓴다.

## 등록된 잡

| 잡 | 주기 | 대상 |
|---|---|---|
| `instagram-publish-due` | `*/5 * * * *` (Asia/Seoul) | `/internal/scheduler/publish-due` |
| `instagram-token-refresh` | 주 1회 | `/internal/scheduler/refresh-token` — 아직 미등록, `docs/instagram-token.md` 참고 |

```bash
# Cloud Scheduler API는 프로젝트에 한 번 켜면 된다
gcloud services enable cloudscheduler.googleapis.com --project van-card-news-generator

# 시크릿은 화면에 찍지 말고 Secret Manager에서 바로 헤더로 넘긴다
SEC=$(gcloud secrets versions access latest --secret=internal-scheduler-secret \
  --project van-card-news-generator)

gcloud scheduler jobs create http instagram-publish-due \
  --location=asia-northeast3 --project=van-card-news-generator \
  --schedule="*/5 * * * *" --time-zone=Asia/Seoul \
  --uri=https://van-card-news-backend-tnkjwa5riq-du.a.run.app/internal/scheduler/publish-due \
  --http-method=POST \
  --headers="X-Scheduler-Secret=$SEC" \
  --max-retry-attempts=3
```

**주기를 5분으로 잡은 이유.** 워커는 `app.publish.worker.grace-minutes=60`을 넘긴 예약을 `expired`로
버린다(다운타임 뒤 하루치를 몰아 올리는 사고를 막는 장치). 트리거 주기가 이 상한에 가까우면 그 장치가
정상 건까지 버린다. 5분은 예약 리드타임 하한 `app.publish.schedule.min-lead-minutes=5`와도 맞는다.

즉시 발행은 이 잡과 무관하다 — `POST /api/contents/{id}/publish/instagram/run`이 요청 안에서 끝낸다.
이 잡이 담당하는 것은 **예약 발행의 정시 실행**이다.

```bash
# 손으로 한 번 돌려보기 (다음 정각을 기다리지 않는다)
gcloud scheduler jobs run instagram-publish-due --location=asia-northeast3 \
  --project van-card-news-generator

# 실제로 두드렸는지는 잡 상태가 아니라 Cloud Run 요청 로그로 확인한다
gcloud logging read 'resource.type="cloud_run_revision" AND httpRequest.requestUrl:"publish-due"' \
  --project van-card-news-generator --limit=5 --freshness=15m \
  --format='value(timestamp,httpRequest.status,httpRequest.latency)'
```

인스턴스가 잠들어 있으면 첫 호출은 콜드스타트로 20초 넘게 걸린다. 잡의 기본 타임아웃(3분) 안이다.

## 향후 승격 경로

시크릿 헤더 → OIDC(구글 서명 JWT 검증). 내부 엔드포인트가 늘거나 보안 요구가 올라가면
`spring-boot-starter-oauth2-resource-server` 추가 + 필터체인 분리로 교체한다.
교체 비용은 지금 만들든 나중에 만들든 같다.
