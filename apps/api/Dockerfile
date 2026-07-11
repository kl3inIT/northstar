# syntax=docker/dockerfile:1.7
# Build context: repo root
#   docker build -f apps/api/Dockerfile -t northstar-api .
#
# Same 3-stage shape as zero-mail's api image (proven on the shared VPS):
# a dependency-resolver stage that caches the Gradle home between builds, a
# builder that layers the boot jar with the SB `tools` jarmode, and a slim
# glibc JRE runtime. Source changes only rebuild from stage 2; dependency
# changes only re-run stage 1's resolve against a persistent cache mount.

# ============================================================================
# STAGE 1: Dependency resolver (cached layer)
# ============================================================================
FROM eclipse-temurin:25-jdk-noble AS deps

ENV GRADLE_USER_HOME=/cache/.gradle \
    LANG=C.UTF-8 \
    TZ=UTC

WORKDIR /build

# Build scripts only — this layer stays valid until a build file changes.
# settings.gradle.kts includes every module, so every module's build file must
# exist for configuration to succeed (sources are NOT needed yet).
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

RUN --mount=type=cache,id=northstar-gradle,target=/cache/.gradle,sharing=locked \
    chmod +x gradlew && \
    ./gradlew :apps:api:dependencies --no-daemon --parallel --console=plain

# ============================================================================
# STAGE 2: Application builder
# ============================================================================
FROM deps AS builder

COPY core/src/main/       core/src/main/
COPY apps/api/src/main/   apps/api/src/main/
COPY integrations/web-openai/src/main/ integrations/web-openai/src/main/

RUN --mount=type=cache,id=northstar-gradle,target=/cache/.gradle,sharing=locked \
    ./gradlew :apps:api:bootJar --no-daemon --parallel --console=plain -x test && \
    mkdir -p /app && \
    mv apps/api/build/libs/*.jar /app/app.jar

# Extract Spring Boot layers with the `tools` jarmode (layertools is
# deprecated). --launcher keeps the JarLauncher classes in the loader layer —
# without it the CMD below ClassNotFounds.
RUN java -Djarmode=tools -jar /app/app.jar extract --layers --launcher --destination /app/extracted

# ============================================================================
# STAGE 3: Runtime — BellSoft Liberica JRE 25 on Alpaquita glibc (~210MB base,
# same libc family as the Temurin builder so native libs stay compatible).
# ============================================================================
FROM bellsoft/liberica-runtime-container:jre-25-glibc

# tini for PID-1 signal handling, curl for the healthcheck probe.
RUN apk add --no-cache tini curl

RUN addgroup -g 1654 -S appgroup && \
    adduser  -u 1654 -S appuser -G appgroup

WORKDIR /app

# Least-churn layers first: third-party deps, loader, snapshots, app classes.
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

EXPOSE 8888

LABEL org.opencontainers.image.title="Northstar API" \
      org.opencontainers.image.description="Northstar API server — Spring Boot 4 / JDK 25" \
      org.opencontainers.image.source="https://github.com/kl3inIT/northstar"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:8888/actuator/health || exit 1

ENTRYPOINT ["/usr/sbin/tini", "--"]
CMD ["java", "org.springframework.boot.loader.launch.JarLauncher"]
