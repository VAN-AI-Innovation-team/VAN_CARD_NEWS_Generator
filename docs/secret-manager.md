# Secret Manager 이관 러너북

Cloud Run의 평문 환경변수에 저장된 운영 시크릿을 Google Cloud Secret Manager로 이관한다.

## 대상

- `OPENAI_API_KEY`
- `DATABASE_PASSWORD`

실제 시크릿 값은 이 문서, GitHub, Linear에 기록하지 않는다.

## 실행 순서

### 1. 현재 Cloud Run 설정 확인

```bash
gcloud run services describe van-card-news-backend \
  --region=asia-northeast3 \
  --format=yaml
```

### 2. Secret Manager 생성

```bash
gcloud secrets create openai-api-key --replication-policy=automatic
gcloud secrets create database-password --replication-policy=automatic
```

실제 값은 표준입력으로 등록한다.

```bash
printf '%s' "$OPENAI_API_KEY" | gcloud secrets versions add openai-api-key --data-file=-
printf '%s' "$DATABASE_PASSWORD" | gcloud secrets versions add database-password --data-file=-
```

### 3. Cloud Run 서비스 계정 권한

```bash
gcloud run services describe van-card-news-backend \
  --region=asia-northeast3 \
  --format='value(spec.template.spec.serviceAccountName)'
```

확인한 서비스 계정에 `roles/secretmanager.secretAccessor` 권한을 부여한다.

### 4. Cloud Run에 Secret 주입

기존 환경변수 이름은 유지하고 값의 출처만 Secret Manager로 변경한다.

```bash
gcloud run services update van-card-news-backend \
  --region=asia-northeast3 \
  --update-secrets=OPENAI_API_KEY=openai-api-key:latest,DATABASE_PASSWORD=database-password:latest
```

### 5. 평문 환경변수 제거 확인

Cloud Run 설정에서 두 값이 일반 환경변수로 남아 있지 않고 Secret Manager 참조로 연결되었는지 확인한다.

### 6. 정상 동작 확인

헬스체크 및 주요 API를 호출해 서비스가 정상 기동하는지 확인한다.

### 7. 과거 리비전 점검

```bash
gcloud run revisions list \
  --service=van-card-news-backend \
  --region=asia-northeast3
```

더 이상 필요하지 않은 평문 시크릿 포함 리비전은 운영 영향 확인 후 삭제한다.

## 완료 기준

- [ ] 실제 시크릿이 GitHub/Linear에 없다.
- [ ] Cloud Run 일반 환경변수에 실제 API Key/DB 비밀번호가 없다.
- [ ] Secret Manager에 두 시크릿이 존재한다.
- [ ] Cloud Run 서비스 계정에 Secret Accessor 권한이 있다.
- [ ] Cloud Run이 Secret Manager 값을 정상 주입받는다.
- [ ] 서비스가 정상 기동한다.
- [ ] 불필요한 평문 시크릿 포함 과거 리비전을 정리했다.

## 주의사항

- 실제 시크릿은 명령어, 커밋, Issue, PR 본문에 직접 입력하지 않는다.
- OPENAI_API_KEY 폐기 전 다른 환경에서 사용하는지 확인한다.
- DB 비밀번호 변경 전 애플리케이션 연결 설정을 확인한다.
- Instagram Access Token은 이 이슈의 대상이 아니며 Instagram 연동 이슈에서 별도로 Secret Manager에 등록한다.
