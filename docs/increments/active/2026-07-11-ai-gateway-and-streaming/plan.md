# Plan

- [x] Replace the manual `SseEmitter` bridge with reactive Vercel SSE encoding.
- [x] Add standard headers, heartbeat comments, step/tool-error/abort parts, and
      exact protocol tests.
- [ ] Commit the streaming transport change independently.
- [ ] Add typed gateway definitions, AI task routes, runtime settings, catalog,
      and route resolution.
- [ ] Move Assistant and background AI tasks to route-selected per-call models.
- [ ] Add safe AI settings APIs and generated web contracts.
- [ ] Add the Chat model picker on web and Flutter, persisted per conversation.
- [ ] Add a generic OpenAI-compatible model catalog client; add 9Router web
      search/fetch adapters only through the existing web-research ports.
- [ ] Update durable specs, tests, roadmap, and the Northstar behavior note.
- [ ] Run static, context-load, web/mobile, runtime, and browser verification.
- [ ] Commit scoped parts, push `main`, and inspect GitHub Actions.
