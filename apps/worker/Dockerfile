# syntax=docker/dockerfile:1.7
# Build context: repo root
#   docker build -f apps/worker/Dockerfile -t northstar-worker .
#
# Same 3-stage shape as the api image (see apps/api/Dockerfile for the full
# rationale): cached dependency resolve, layered boot jar, slim glibc JRE.
# Differences: no port (web-application-type: none) and a process-liveness
# healthcheck instead of an HTTP probe — the worker serves nothing.

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
COPY integrations/news-huggingnews/build.gradle.kts integrations/news-huggingnews/build.gradle.kts

RUN --mount=type=cache,id=northstar-gradle,target=/cache/.gradle,sharing=locked \
    chmod +x gradlew && \
    ./gradlew :apps:worker:dependencies --no-daemon --parallel --console=plain

# ============================================================================
# STAGE 2: Application builder
# ============================================================================
FROM deps AS builder

COPY core/src/main/          core/src/main/
COPY apps/worker/src/main/   apps/worker/src/main/
COPY integrations/web-openai/src/main/ integrations/web-openai/src/main/
COPY integrations/web-firecrawl/src/main/ integrations/web-firecrawl/src/main/
COPY integrations/ai-openai-compatible/src/main/ integrations/ai-openai-compatible/src/main/

RUN --mount=type=cache,id=northstar-gradle,target=/cache/.gradle,sharing=locked \
    ./gradlew :apps:worker:bootJar --no-daemon --parallel --console=plain -x test && \
    mkdir -p /app && \
    mv apps/worker/build/libs/*.jar /app/app.jar

RUN java -Djarmode=tools -jar /app/app.jar extract --layers --launcher --destination /app/extracted

# ============================================================================
# STAGE 3: Runtime
# ============================================================================
FROM bellsoft/liberica-runtime-container:jre-25-glibc

# tini for PID-1 signal handling; pgrep (procps) for the liveness healthcheck.
RUN apk add --no-cache tini procps

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

LABEL org.opencontainers.image.title="Northstar Worker" \
      org.opencontainers.image.description="Northstar background worker — Spring Boot 4 / JDK 25" \
      org.opencontainers.image.source="https://github.com/kl3inIT/northstar"

# No HTTP surface: alive = the JVM is still running. -XX:+ExitOnOutOfMemoryError
# above turns a dead heap into a dead process, which this check then catches.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD pgrep -f "org.springframework.boot.loader.launch.JarLauncher" || exit 1

ENTRYPOINT ["/usr/sbin/tini", "--"]
CMD ["java", "org.springframework.boot.loader.launch.JarLauncher"]
