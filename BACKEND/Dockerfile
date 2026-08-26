# Build Stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /web

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew

COPY src src

RUN ./gradlew clean bootJar --no-daemon

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /web

# 한글을 포함한 CJK 문자가 이미지 렌더링 시
# 네모(□)로 표시되지 않도록 Noto CJK 폰트를 설치합니다.
RUN apk add --no-cache font-noto-cjk

# 웹을 root가 아닌 권한이 제한된 별도 사용자로 실행합니다.
RUN addgroup --system spring \
    && adduser --system --ingroup spring spring

COPY --from=builder /web/build/libs/*.jar web.jar

# 애플리케이션 파일 및 업로드/생성 디렉터리 권한 설정
RUN mkdir -p /app/uploads /app/generated \
    && chown -R spring:spring /app/uploads /app/generated \
    && chown spring:spring /web/web.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "/web/web.jar"]
