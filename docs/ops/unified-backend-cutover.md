# Unified Backend Cutover

The consolidation release replaces `northstar-api`, `northstar-mcp`, and
`northstar-worker` with `northstar-server`. Publishing or merging code does not
perform this cutover; the `Deploy` workflow is manual-only.

## Preconditions

- CI and both `northstar-server` and `northstar-web` image builds are green for
  the selected full commit SHA from `main`.
- `.env` contains a generated `NORTHSTAR_MCP_TOKEN`, and each approved MCP client
  sends it as `X-Northstar-MCP-Token`.
- A current `pg_dump` exists and the deploy workflow can write `backups/`.
- `/data/nginx/proxy_host/9.conf.disabled` exists, the active route does not, and
  `nginx -t` passes.
- The legacy image IDs and Compose topology are still available for rollback.

## Deploy and verify internally

Dispatch `Deploy` with the reviewed exact 40-character commit SHA. The
workflow derives the immutable `sha-<full-commit>` image tag and snapshots the
running topology. It dumps the database, stops legacy backend processes, and
starts `northstar-server` plus `northstar-web`.

Before it succeeds, the workflow verifies:

- server and web container health;
- anonymous REST rejection and CSRF bootstrap;
- anonymous MCP rejection plus token-authenticated `initialize`, session
  establishment, `tools/list`, and one read-only tool;
- valid Nginx configuration.

The workflow reloads existing NPM configuration but never enables a disabled
Northstar route.

## Edge cutover

Only after internal REST, session, MCP, Flyway, and jobs checks are healthy:

> `/mcp` intentionally does not use the REST session login and exposes both
> read-only and mutating tools, including delete-capable operations. Every MCP
> request must send `X-Northstar-MCP-Token` matching `NORTHSTAR_MCP_TOKEN`.
> Keep it behind the approved edge isolation/access policy as defense in depth;
> never treat the disabled NPM route itself as an authorization control.

1. Change every Northstar backend upstream from `northstar-api:8888` or
   `northstar-mcp:8081` to `northstar-server:8888`.
2. Keep REST paths unchanged and route MCP to `/mcp` on the same server.
3. Run `nginx -t` before enabling the host configuration.
4. Enable the route, reload Nginx, and smoke the web root, authenticated REST,
   token-authenticated MCP `initialize`, `tools/list`, and one read-only tool.

## Rollback

On deploy failure, the workflow uses `.last-deploy.txt`,
`backups/docker-compose.rollback.yml`, and the matching infra Compose snapshot
to restore either the prior unified server or the legacy three-container
backend. The workflow remains red after a successful rollback so the failure
is investigated.

Do not dispatch another deployment or prune the legacy images until the edge
observation window is accepted: the next deployment intentionally replaces the
single local rollback snapshot.

If the workflow succeeded but the later edge smoke fails, disable the NPM route
first, then restore the saved topology from `/apps/north-star`:

```bash
set -euo pipefail
cp backups/docker-compose.rollback.yml docker/docker-compose.yml
cp backups/docker-compose.infra.rollback.yml docker/docker-compose.infra.yml
export NORTHSTAR_WEB_IMAGE="$(awk '/^northstar-web /{print $2}' .last-deploy.txt)"
test -n "$NORTHSTAR_WEB_IMAGE"

if awk '/^northstar-server /{found=1} END{exit !found}' .last-deploy.txt; then
  export NORTHSTAR_SERVER_IMAGE="$(awk '/^northstar-server /{print $2}' .last-deploy.txt)"
  test -n "$NORTHSTAR_SERVER_IMAGE"
  docker compose -f docker/docker-compose.infra.yml -f docker/docker-compose.yml \
    --env-file .env up -d --no-build server web
else
  docker rm -f northstar-server 2>/dev/null || true
  export NORTHSTAR_API_IMAGE="$(awk '/^northstar-api /{print $2}' .last-deploy.txt)"
  export NORTHSTAR_MCP_IMAGE="$(awk '/^northstar-mcp /{print $2}' .last-deploy.txt)"
  export NORTHSTAR_WORKER_IMAGE="$(awk '/^northstar-worker /{print $2}' .last-deploy.txt)"
  [[ -n "$NORTHSTAR_API_IMAGE" && -n "$NORTHSTAR_MCP_IMAGE" && -n "$NORTHSTAR_WORKER_IMAGE" ]]
  docker compose -f docker/docker-compose.infra.yml -f docker/docker-compose.yml \
    --env-file .env up -d --no-build api web mcp worker
fi
```

Verify the restored health endpoint before retargeting and re-enabling NPM. If a
migration partially applied, stop the replacement server and restore the
matching `backups/pre-deploy-*.dump` before restarting the previous topology.
