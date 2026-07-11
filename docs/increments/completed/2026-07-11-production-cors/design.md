# Production CORS Design

## Goal

Make browser cross-origin access explicit and production-safe without coupling
native Flutter clients to browser CORS behavior.

## Decisions

- Native iOS and Android require no CORS configuration.
- Same-origin web deployments leave the allowlist empty.
- Cross-origin browser builds use a comma-separated list of exact HTTP(S)
  origins from `NORTHSTAR_CORS_ALLOWED_ORIGINS`.
- Paths, queries, fragments, embedded credentials, non-HTTP schemes, and
  wildcards fail application startup instead of silently broadening access.
- Cross-origin cookies remain disabled. Browser previews authenticate with
  Bearer tokens, while the main web app keeps its same-origin session and CSRF
  flow.
- Allowed methods cover normal REST traffic; allowed request headers remain a
  small explicit list.

## Verification

Integration coverage must prove exact-origin matching, multiple configured
origins, credential exclusion, normal REST preflight, and rejection of both
suffix-spoofed origins and unlisted headers.
