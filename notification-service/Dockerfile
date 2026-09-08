FROM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /workspace

COPY . .
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :notification-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-noble AS runtime
RUN groupadd --system spring && useradd --system --gid spring spring

WORKDIR /app
COPY --from=builder --chown=spring:spring /workspace/notification-service/build/libs/*.jar app.jar

USER spring:spring

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]