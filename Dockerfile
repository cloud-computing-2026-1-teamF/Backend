# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts settings.gradle.kts ./
COPY src src
# 보고서 프롬프트 계약(reports/*.json)은 build.gradle 의 srcDir("reports")로 클래스패스에 포함된다.
# 이 폴더를 빌드 컨텍스트에 복사하지 않으면 prompt_template.json 이 jar 에서 누락되어 보고서 생성이 500 실패.
COPY reports reports
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
