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

Cloud Scheduler 잡은 두드릴 로직이 생기는 시점(예약 발행 / 토큰 갱신 이슈)에 만든다.
잡을 만들 때 헤더로 같은 시크릿을 실어 보내고, **재시도는 앱 코드가 아니라 잡의 재시도 설정**을 쓴다.

## 향후 승격 경로

시크릿 헤더 → OIDC(구글 서명 JWT 검증). 내부 엔드포인트가 늘거나 보안 요구가 올라가면
`spring-boot-starter-oauth2-resource-server` 추가 + 필터체인 분리로 교체한다.
교체 비용은 지금 만들든 나중에 만들든 같다.
