# syntax=docker/dockerfile:1.7
# Build context: repo root
#   docker build -f apps/mcp/Dockerfile -t northstar-mcp .
#
# Twin of apps/api/Dockerfile (same 3 stages, same cache mount id so the two
# images share one Gradle cache) targeting :apps:mcp on :8081.

# ============================================================================
# STAGE 1: Dependency resolver (cached layer)
# ============================================================================
FROM eclipse-temurin:25-jdk-noble AS deps

ENV GRADLE_USER_HOME=/cache/.gradle \
    LANG=C.UTF-8 \
    TZ=UTC

WORKDIR /build

COPY gradle/                          gradle/
COPY gradlew                          gradlew
COPY settings.gradle.kts              settings.gradle.kts
COPY build.gradle.kts                 build.gradle.kts
COPY gradle.properties                gradle.properties
COPY build-logic/                     build-logic/
COPY core/build.gradle.kts            core/build.gradle.kts
COPY apps/api/build.gradle.kts        apps/api/build.gradle.kts
COPY apps/mcp/build.gradle.kts        apps/mcp/build.gradle.kts
COPY apps/worker/build.gradle.kts     apps/worker/build.gradle.kts
COPY integrations/web-openai/build.gradle.kts integrations/web-openai/build.gradle.kts
COPY integrations/web-firecrawl/build.gradle.kts integrations/web-firecrawl/build.gradle.kts
COPY integrations/web-nine-router/build.gradle.kts integrations/web-nine-router/build.gradle.kts
COPY integrations/ai-openai-compatible/build.gradle.kts integrations/ai-openai-compatible/build.gradle.kts
COPY integrations/speech-azure/build.gradle.kts integrations/speech-azure/build.gradle.kts

RUN --mount=type=cache,id=northstar-gradle,target=/cache/.gradle,sharing=locked \
    chmod +x gradlew && \
    ./gradlew :apps:mcp:dependencies --no-daemon --parallel --console=plain

# ============================================================================
# STAGE 2: Application builder
# ============================================================================
FROM deps AS builder

COPY core/src/main/       core/src/main/
COPY apps/mcp/src/main/   apps/mcp/src/main/
COPY integrations/web-openai/src/main/ integrations/web-openai/src/main/
COPY integrations/web-firecrawl/src/main/ integrations/web-firecrawl/src/main/

RUN --mount=type=cache,id=northstar-gradle,target=/cache/.gradle,sharing=locked \
    ./gradlew :apps:mcp:bootJar --no-daemon --parallel --console=plain -x test && \
    mkdir -p /app && \
    mv apps/mcp/build/libs/*.jar /app/app.jar

RUN java -Djarmode=tools -jar /app/app.jar extract --layers --launcher --destination /app/extracted

# ============================================================================
# STAGE 3: Runtime
# ============================================================================
FROM bellsoft/liberica-runtime-container:jre-25-glibc

RUN apk add --no-cache tini curl

RUN addgroup -g 1654 -S appgroup && \
    adduser  -u 1654 -S appuser -G appgroup

WORKDIR /app

COPY --from=builder --chown=1654:1654 /app/extracted/dependencies/          ./
COPY --from=builder --chown=1654:1654 /app/extracted/spring-boot-loader/    ./
COPY --from=builder --chown=1654:1654 /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=1654:1654 /app/extracted/application/           ./

USER 1654:1654

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:+UseStringDeduplication \
  -XX:+ParallelRefProcEnabled \
  -XX:+DisableExplicitGC \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom \
  -Djava.awt.headless=true \
  -Dfile.encoding=UTF-8"

EXPOSE 8081

LABEL org.opencontainers.image.title="Northstar MCP" \
      org.opencontainers.image.description="Northstar MCP server (streamable-http) — Spring Boot 4 / JDK 25" \
      org.opencontainers.image.source="https://github.com/kl3inIT/northstar"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["/usr/sbin/tini", "--"]
CMD ["java", "org.springframework.boot.loader.launch.JarLauncher"]
