# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /workspace

COPY . .
RUN chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :order-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-noble AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system spring && useradd --system --gid spring spring

WORKDIR /app
COPY --from=builder --chown=spring:spring /workspace/order-service/build/libs/*.jar app.jar

USER spring:spring

ARG SERVER_PORT=8084
ENV SERVER_PORT=${SERVER_PORT}
EXPOSE ${SERVER_PORT}

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=10 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]