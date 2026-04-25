FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts settings.gradle.kts ./
COPY src src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
