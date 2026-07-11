# Plan

- [x] Replace the manual `SseEmitter` bridge with reactive Vercel SSE encoding.
- [x] Add standard headers, heartbeat comments, step/tool-error/abort parts, and
      exact protocol tests.
- [x] Commit the streaming transport change independently.
- [x] Add typed gateway definitions, AI task routes, runtime settings, catalog,
      and route resolution.
- [x] Move Assistant and background AI tasks to route-selected per-call models.
- [x] Add safe AI settings APIs and generated web contracts.
- [x] Add the Chat model picker on web and Flutter, persisted per conversation.
- [x] Add a generic OpenAI-compatible model catalog client; add 9Router web
      search/fetch adapters only through the existing web-research ports.
- [x] Add encrypted runtime gateway CRUD and connection testing; remove the
      product-level 9Router gateway entry from default YAML.
- [x] Replace the Chat select with the official AI Elements Model Selector.
- [x] Use native Attachments, Message Actions, Inline Citation, and Sources;
      emit structured source-url parts from Assistant web tools.
- [x] Emit knowledge-base notes/files as source-document parts and restore all
      structured sources from chat history.
- [x] Compose the Assistant input from native Prompt Input attachment,
      screenshot, drag/drop, tools, submit, and stop controls.
- [x] Update durable specs, tests, roadmap, and the Northstar behavior note.
- [x] Run static, context-load, web/mobile, runtime, and browser verification.
- [ ] Commit scoped parts, push `main`, and inspect GitHub Actions.
