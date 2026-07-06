# Deploying Northstar

Northstar shares the zero-mail VPS: same nginx-proxy-manager (NPM) edge, same
GHCR + self-hosted-runner deploy shape, but its **own** networks, Postgres
container/volume and `.env`. Nothing is shared with zero-mail except the box
and the NPM container.

## Topology

```
internet ── NPM (:443, TLS)
              ├─ <your-domain>/        → northstar-web:8080   (static SPA)
              └─ <your-domain>/api     → northstar-api:8888   (Spring API)
                         docker network: northstar-proxy (external)
northstar-api ── northstar-postgres:5432 (pgvector/pgvector:pg18)
                         docker network: northstar-internal (external)
```

The web bundle calls the API at same-origin `/api`, so no domain is baked into
the web image — one image works for any hostname.

## One-time server bootstrap

```bash
# 1. Clone + env
sudo mkdir -p /apps && cd /apps
git clone https://github.com/kl3inIT/northstar.git northstar
cd northstar && mkdir -p backups
cp docker/env.server.example .env && vi .env   # POSTGRES_PASSWORD, OPENAI_API_KEY

# 2. Networks (external — survive compose down)
docker network create northstar-proxy
docker network create northstar-internal
docker network connect northstar-proxy nginx-proxy-manager

# 3. First start (build on box, or `docker compose ... pull` once CI has pushed)
docker compose -f docker/docker-compose.infra.yml -f docker/docker-compose.yml \
  --env-file .env up -d --build postgres api web
```

## NPM proxy host

Add ONE proxy host in the NPM UI for the Northstar domain:

- **Details**: forward to `northstar-web` port `8080`, scheme `http`.
  Enable *Block Common Exploits* + *Websockets Support*. SSL: request a Let's
  Encrypt cert, force SSL + HTTP/2.
- **Custom locations**: add `/api` → `northstar-api` port `8888`, and in that
  location's gear/advanced box paste:

  ```nginx
  # Assistant chat streams over SSE — do not buffer, allow long idle reads.
  proxy_buffering off;
  proxy_cache off;
  proxy_read_timeout 300s;
  proxy_set_header Connection '';
  proxy_http_version 1.1;
  ```

⚠️ NPM resolves `northstar-api` to a container IP **at config load**. After any
`docker compose up -d` that recreates the api, run
`docker exec nginx-proxy-manager nginx -s reload` — the deploy workflow does
this automatically; remember it when deploying by hand. (This exact miss caused
zero-mail's 2026-06-16 login outage.)

## CI/CD

- **Build and Push Images** (`.github/workflows/build-and-push.yml`): every
  push to `main` publishes `ghcr.io/kl3init/northstar-{api,web,mcp}` tagged
  `:main` + `:sha-<short>`, with per-component buildx caches.
- **Deploy** (`.github/workflows/deploy.yml`): manual dispatch, runs on a
  self-hosted runner ON the VPS (labels `[self-hosted, northstar]`). Order of
  operations: pg_dump backup → snapshot running image IDs → pull + recreate →
  readiness wait → NPM reload → public smoke probe (`/` AND `/api`) → on any
  failure, auto-rollback to the snapshotted images.
  Set the repo variable `NORTHSTAR_BASE_URL` (e.g. `https://northstar.example.com`)
  for the smoke step. Roll back on purpose by dispatching with a `sha-<short>` tag.
- Register the runner: repo → Settings → Actions → Runners → New self-hosted
  runner, install under `/apps/actions-runner-northstar`, add the extra label
  `northstar` (zero-mail's runner uses `prod`, so the two never collide).

Simplifications vs zero-mail (deliberate, single-developer): no semver release
train or prod-environment reviewers — `main` is the release; rollback safety
comes from the image-ID snapshot + DB dump instead of version gates.

## What is intentionally NOT deployed

- **worker** — still an empty shell (no jobs/listeners). Add a `worker`
  service mirroring `api` in `docker/docker-compose.yml` when the first async
  job lands.
- **mcp** — behind the `mcp` compose profile. Before exposing it through NPM,
  put an auth token in front (MCP has no login of its own).
- **Auth**: the app itself has no login yet. Keep the domain private (VPN /
  IP allowlist in NPM / NPM Access List with basic auth) until real auth lands.

## Local dev is unchanged

`compose.yaml` at the repo root remains the dev database
(`docker compose up -d` + `bootRun` + `pnpm dev`); the `docker/` folder is
prod-only.
