# Web Research Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Runtime provider switch and retryable fallback | Automated | `core/src/test/java/com/northstar/core/web/WebResearchServiceTests.java` uses fake providers and a persisted-setting double. |
| Modulith boundary | Automated | `ModulithVerificationTests`; `core.web` exposes contracts without importing delivery adapters. |
| OpenAI Responses request and citation parsing | Automated | `OpenAiWebSearchProviderTests` verifies allowed-domain request shape, answer parsing, deduplication, title upgrade, and blocked-source filtering. |
| Direct reader HTML extraction | Automated | `DirectWebPageReaderTests` verifies main-text/title extraction and removal of navigation/scripts. |
| SSRF and redirect revalidation | Automated | `DirectWebPageReaderTests` rejects private DNS results and a redirect whose next resolution becomes private. |
| Firecrawl rendered-page reading | Automated + live smoke | `FirecrawlWebPageReaderTests` verifies the bounded main-Markdown request, page mapping, truncation, and retryable quota failure. A 2026-07-11 live smoke also completed Search, Scrape, one-page Crawl, and Interact cleanup; only Scrape is exposed as a page reader. |
| Settings GET/update/reset and secret boundary | Automated | `WebResearchControllerIntegrationTests` runs PostgreSQL, checks OpenAI and Firecrawl provider metadata, round-trips the override, resets defaults, and asserts no key is exposed. |
| Assistant-only tool discovery | Automated | `WebResearchControllerIntegrationTests` confirms API Assistant discovery; the tool class has no `@McpTool`, while existing MCP tool-list tests protect the public surface. |
| Settings desktop/mobile rendering | Runtime | Playwright on 2026-07-11 verified `/settings` at desktop and 390x844, save/reset state, no overflow, and no console errors. |
| Live search and page read | Runtime | API Assistant turns on 2026-07-11 invoked real `search_web` with official Spring sources and `read_web_page` for `https://example.com`, including Markdown citations; verification conversations were deleted afterward. |
| DNS rebinding between validation and socket connection | Residual risk | JDK HttpClient resolves independently after policy validation. Every redirect is rechecked, but pinning a validated DNS result for TLS requires a future transport with an injectable DNS resolver. |
