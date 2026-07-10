# Web Research Spec

## Current Behavior

Northstar Assistant can search current public information and read the main
text of an ordinary public HTTP(S) page. YouTube, PDF extraction, crawling,
authenticated pages, and browser-rendered pages are not supported in V1.

Search and page reading are separate capabilities behind `core.web` contracts.
The current adapters are:

- `openai`: OpenAI Responses with the hosted `web_search` tool. Results carry a
  concise answer, deduplicated source URLs/titles, the actual provider, fetch
  time, and any fallback origin.
- `direct`: Java HTTP plus Jsoup for HTML/text. It follows redirects manually,
  revalidates every target, and returns title, normalized main text, final URL,
  content type, truncation state, reader, and fetch time.

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
`NORTHSTAR_WEB_*` and the existing `OPENAI_API_KEY`. Adding a provider means
registering a new `WebSearchProvider` and/or `WebPageReader` bean and adding its
id to configuration. Assistant tool schemas and Settings REST contracts remain
unchanged.

## Safety

- Only HTTP(S) URLs without user-info are accepted.
- DNS is checked before every hop; local, private, link-local, multicast,
  carrier-grade NAT, metadata, documentation, and other non-public ranges are
  blocked.
- Redirects are manual and bounded; each target is checked again.
- Connection/read time, bytes, characters, redirects, and media types are
  bounded. Responses are requested without compression so byte limits remain
  enforceable.
- Page text is untrusted tool data and cannot override Assistant instructions.
- Web tools exist only in the authenticated in-app Assistant delivery surface,
  not the public MCP server.

## Source Modules

- `core.web`
- `apps/api.webresearch`
- `apps/api.assistant.WebResearchTools`
- `web/pages/settings`
