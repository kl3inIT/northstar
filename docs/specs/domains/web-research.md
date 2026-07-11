# Web Research Spec

## Current Behavior

Northstar Assistant can search current public information and read the main
text of a public HTTP(S) page. YouTube, PDF extraction, whole-site crawling,
authenticated pages, and interactive browser actions are not supported in V1.

Search and page reading are separate capabilities behind `core.web` contracts.
The current adapters are:

- `openai`: OpenAI Responses with the hosted `web_search` tool. Results carry a
  concise answer, deduplicated source URLs/titles, the actual provider, fetch
  time, and any fallback origin.
- `direct`: Java HTTP plus Jsoup for HTML/text. It follows redirects manually,
  revalidates every target, and returns title, normalized main text, final URL,
  content type, truncation state, reader, and fetch time.
- `firecrawl`: optional Firecrawl Scrape for rendered or bot-protected pages.
  It requests main-content Markdown with the basic proxy, caps response bytes
  and characters, and returns the same provider-neutral page result.

`WebResearchService` reads the effective selection for every call, so saving a
runtime override changes the next Assistant request without restarting. The
optional database row overrides application defaults; deleting it restores
configuration. Search and page caches are provider-keyed, bounded, and expire.
Fallback is opt-in and only advances for rate limits or temporary provider
unavailability. Input, unsupported-content, blocked-address, and size failures
never fall through to another provider.

## Settings Contract

The general `/settings` page currently has a `Web research` section. It can:

- enable or disable Assistant web access;
- select one configured search provider and page reader independently;
- enable the server-configured fallback order;
- show provider capability/configured status;
- save a runtime override or restore application defaults.

REST endpoints are `GET/PUT /api/settings/web-research`,
`DELETE /api/settings/web-research/override`, and
`GET /api/settings/web-research/providers`. Responses expose ids,
capabilities, display names, and configured state, never API keys.

Defaults and credentials are under `northstar.web`; environment overrides use
`NORTHSTAR_WEB_*`, `OPENAI_API_KEY`, and `FIRECRAWL_API_KEY`. Direct remains the
default reader; `firecrawl` is present in the configured page-reader fallback
order and becomes selectable when its key is configured. Adding a provider
means registering a new `WebSearchProvider` and/or `WebPageReader` bean and
adding its id to configuration. Assistant tool schemas and Settings REST
contracts remain unchanged.

## Safety

- Only HTTP(S) URLs without user-info are accepted.
- The direct reader checks DNS before every hop; local, private, link-local,
  multicast, carrier-grade NAT, metadata, documentation, and other non-public
  ranges are blocked. Redirects are manual, bounded, and revalidated.
- The Firecrawl reader rejects non-HTTP(S), credential-bearing, localhost, and
  local-domain URLs before delegating the fetch to Firecrawl's remote sandbox.
- Connection/read time, bytes, characters, redirects, and media types are
  bounded where the provider exposes them. Direct responses are requested
  without compression so its byte limit remains enforceable.
- Page text is untrusted tool data and cannot override Assistant instructions.
- Firecrawl Scrape is the only remote browser-backed page capability exposed;
  Crawl and Interact are intentionally absent from the provider/tool contracts.
- Web tools exist only in the authenticated in-app Assistant delivery surface,
  not the public MCP server.

## Source Modules

- `core.web`
- `integrations.web-openai`
- `integrations.web-firecrawl`
- `apps/api.webresearch`
- `apps/api.assistant.WebResearchTools`
- `web/pages/settings`
