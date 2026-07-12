# 0027: External live feeds remain separate from Northstar Briefs

## Status

Accepted on 2026-07-12.

## Context

HuggingNews has a useful dense newsroom interaction model and public page-data
routes with ranked AI stories and inline detail. Northstar also has an existing
Morning Brief automation whose output is durable, private, user-configured,
and executed by the worker. Combining both into one stream would blur content
ownership, freshness, persistence, and failure behavior.

## Decision

`/briefs` has two sibling tabs with the same editorial interaction grammar but
different data boundaries:

- HuggingNews is an external, read-only live feed. A provider adapter consumes
  only its public SvelteKit page-data routes, maps them into provider-neutral
  `core.brief` records, bounds and caches responses, and never persists viewed
  stories.
- Northstar Brief remains app-owned automation output stored as notes with
  durable scheduler runs, retries, history, and explicit user configuration.
  Its settings and run controls live in its own tab, while the stable
  `morning-brief.v1` type remains available to API, Assistant, MCP, and worker
  infrastructure but is hidden from the generic Settings list.

Northstar does not call HuggingNews's internal Convex backend, depend on a
signed-in Clerk session, or copy its implementation. The UI adopts only the
publicly observable information architecture and rebuilds it with Northstar's
React and shadcn primitives.

## Consequences

- A provider outage can degrade or stale only the HuggingNews tab; Northstar
  issues and scheduler history remain available.
- Provider response changes are isolated to one integration module and pinned
  by captured public response fixtures.
- Adding another live publisher requires another adapter and an explicit
  product decision; it does not automatically become a Northstar Brief source.
- Users configure the private brief where they read it, while generic
  automation infrastructure remains reusable and unchanged.
