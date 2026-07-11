# syntax=docker/dockerfile:1.6
# ---------------------------------------------------------------------------
# TaskFlow backend Dockerfile (multi-stage)
#
# Build context: the repository root (so we can fall back to common files if
# needed). Compose invokes it as:
#   build: { context: .., dockerfile: backend/Dockerfile }
# ---------------------------------------------------------------------------

# ============ Stage 1: build ============
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Cache the dependency layer separately — only invalidated when pom.xml changes,
# not on every source edit. Cuts rebuild time from ~minutes to ~seconds.
COPY backend/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline

COPY backend/src ./src
RUN mvn -B -ntp package -DskipTests \
 && mv target/*.jar target/app.jar

# ============ Stage 2: runtime ============
# Alpine variant keeps the final image small (~180 MB) and ships wget for the
# HEALTHCHECK without an extra apt-install step.
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Non-root user — containers should never run as root in production.
RUN addgroup -S taskflow && adduser -S taskflow -G taskflow

COPY --from=build /workspace/target/app.jar /app/app.jar

USER taskflow:taskflow

EXPOSE 8080

# Container-aware JVM flags:
#   MaxRAMPercentage=75 — let the JVM grow into 75% of the cgroup memory limit
#   G1GC                — best balance for typical request-response workloads
#   /dev/./urandom      — avoid blocking SecureRandom on entropy-starved hosts
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

# Spring Boot's /actuator/health is exposed unauthenticated by default — compose
# uses this to gate downstream services that depend on the API being ready.
HEALTHCHECK --interval=15s --timeout=5s --retries=6 --start-period=45s \
  CMD wget -qO- http://localhost:8080/actuator/health > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
