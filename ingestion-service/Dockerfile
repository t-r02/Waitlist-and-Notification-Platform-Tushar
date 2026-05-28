# ── Stage 1: build ───────────────────────────────────────────────────────────
# Build context is the repo root so the :events sub-project is available.
FROM gradle:8.5-jdk21 AS build
WORKDIR /app
# Copy the entire monorepo (build context = repo root)
COPY --chown=gradle:gradle . /app
RUN gradle :ingestion-service:bootJar --no-daemon -q

# ── Stage 2: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/ingestion-service/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
