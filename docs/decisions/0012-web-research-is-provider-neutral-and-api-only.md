# 0012 - Web Research Is Provider-Neutral And API-Only

## Status

Accepted.

## Context

Assistant needs current public information and ordinary pasted-link reading.
The current Spring AI OpenAI starter covers Chat Completions, not the Responses
web-search surface, and binding tools directly to one vendor would make provider
changes, fallback, tests, and later readers expensive. The existing MCP endpoint
is public, so publishing a paid search/fetch tool there would also create an
uncontrolled cost and abuse surface.

The community `spring-ai-agent-utils` repository provided useful patterns for
search result shape, source-oriented tool descriptions, bounded fetches, and
short caching. Its Brave adapter is provider-specific, while its smart fetcher's
domain check is not a complete SSRF boundary and its error handling collapses
provider failures into empty results. Depending on or vendoring it would not
remove the architectural work Northstar needs.

## Decision

- Keep provider-neutral `WebSearchProvider` and `WebPageReader` contracts in
  `core.web`; provider adapters remain in delivery apps.
- Route both capabilities through one service that reads a typed runtime
  setting for every call. Application configuration is the resettable default;
  an optional database row is the live override.
- Select search and page-reader providers independently. The model chooses a
  capability through a tool, never a vendor id.
- Use OpenAI Responses hosted `web_search` for V1 search and a bounded direct
  Java HTTP/Jsoup adapter for ordinary HTML/text.
- Make fallback explicit and limited to retryable provider failures. Include
  the actual provider and fallback origin in results.
- Expose web tools only to the in-app Assistant. Do not annotate them as MCP
  tools or publish a fetch/search REST endpoint.
- Keep secrets in server configuration and expose only safe provider metadata
  to the general Settings page.
- Defer YouTube, PDFs, browser rendering, crawling, and automatic note ingestion
  to specialized future readers.

## Consequences

Tavily, Brave, SearXNG, a browser reader, or a YouTube transcript reader can be
added as beans and configuration without changing Assistant tool schemas.
Runtime switches take effect without restart and do not leak credentials.
Northstar owns more adapter and safety code than it would with a single vendor
library, but it also owns failure semantics, SSRF policy, and the public/private
tool boundary. Direct reading still has a residual DNS-rebinding window because
JDK HttpClient resolves again when it opens the socket; a future pinned-DNS
transport can replace the adapter behind the same contract.
