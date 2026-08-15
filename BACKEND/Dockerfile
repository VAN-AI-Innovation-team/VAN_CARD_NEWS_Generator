# Build Stage
FROM eclipse-temurin:26-jdk-alpine AS builder

WORKDIR /web

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew

COPY src src

RUN ./gradlew clean bootJar --no-daemon

# Runtime Stage
FROM eclipse-temurin:26-jre-alpine

WORKDIR /web

# 웹을 root가 아닌 권한이 제한된 별도 사용자로 실행합니다.
RUN addgroup --system spring \
    && adduser --system --ingroup spring spring

COPY --from=builder /web/build/libs/*.jar web.jar

RUN chown spring:spring /web/web.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/web/web.jar"]