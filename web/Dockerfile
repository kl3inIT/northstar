# syntax=docker/dockerfile:1.7
# Build context: repo root (the typed client is generated from contracts/):
#   docker build -f web/Dockerfile -t northstar-web .
#
# Vite SPA → static nginx runtime, following zero-mail's admin image (the
# closer template than its Next.js web): pnpm-store cache mount, non-root
# nginx on an unprivileged port, SPA history fallback, /healthz probe.

# ============================================================================
# STAGE 1: Builder
# ============================================================================
FROM node:24-alpine AS builder

RUN corepack enable
WORKDIR /repo/web

# Manifest first — the install layer survives source-only changes.
COPY pnpm-workspace.yaml /repo/pnpm-workspace.yaml
COPY web/package.json web/pnpm-lock.yaml ./
RUN --mount=type=cache,id=northstar-pnpm-store,target=/pnpm-store \
    pnpm config set store-dir /pnpm-store && \
    pnpm install --frozen-lockfile

# Hey API output is gitignored — regenerate it from the committed contract.
COPY contracts/openapi.json /repo/contracts/openapi.json
COPY web/ .
RUN pnpm gen:api && pnpm build

# ============================================================================
# STAGE 2: Runtime — static nginx, non-root, unprivileged port 8080
# ============================================================================
FROM nginx:1.29-alpine

RUN apk add --no-cache curl

COPY web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /repo/web/dist /usr/share/nginx/html

# Run the whole server as a non-root uid; pre-chown the dirs nginx writes to.
RUN addgroup -g 1654 -S appgroup && \
    adduser  -u 1654 -S appuser -G appgroup && \
    sed -i '/^user /d' /etc/nginx/nginx.conf && \
    sed -i -E 's|pid\s+/(var/)?run/nginx.pid;|pid /tmp/nginx.pid;|' /etc/nginx/nginx.conf && \
    chown -R 1654:1654 /var/cache/nginx /var/log/nginx /usr/share/nginx/html

USER 1654:1654

EXPOSE 8080

LABEL org.opencontainers.image.title="Northstar Web" \
      org.opencontainers.image.description="Northstar web SPA — Vite + React static bundle" \
      org.opencontainers.image.source="https://github.com/kl3inIT/northstar"

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -fsS http://localhost:8080/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
