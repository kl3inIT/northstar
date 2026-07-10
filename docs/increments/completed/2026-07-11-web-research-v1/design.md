# Web Research V1

## Problem

Northstar's assistant can only use persisted personal data. It cannot answer
questions that require current public information or inspect an ordinary URL
the user pastes. Adding provider-specific tool classes directly would make
search configuration, fallback, testing, and later provider changes brittle.

## Scope

V1 adds:

- provider-neutral web search and page-reading contracts;
- an OpenAI Responses web-search adapter using the existing API key;
- a direct HTML/text reader with SSRF, redirect, timeout, media-type, byte, and
  character limits;
- runtime provider selection stored as a typed setting, with application config
  as the resettable default;
- `search_web` and `read_web_page` tools for the in-app assistant only;
- source-aware typed results and concise web-source presentation in chat;
- a general `/settings` page with section navigation; Web research is its first
  section and can enable the capability or switch configured providers without
  restarting the application. Later settings areas reuse the same shell.

YouTube transcripts, PDFs, crawling, browser-rendered pages, automatic note
ingestion, and public MCP exposure are outside V1.

## Architecture

`core.web` owns requests, results, provider/reader contracts, the provider
registry/router, and the persisted runtime selection. Provider adapters live in
the delivery app. The model never chooses a provider: tools call one
`WebResearchService`, which reads the effective runtime setting on every call.

Search and page reading are separate capabilities. OpenAI implements search;
the direct reader implements ordinary HTML/text. A later Tavily bean can
implement both, Brave only search, and YouTube a specialized reader without
changing tools or routing.

Secrets remain environment/application configuration. REST exposes only
provider ids, capabilities, and `configured`, never credentials. Clearing the
runtime override restores the configured defaults.

## Safety

- Only HTTP(S) URLs without user-info are accepted.
- DNS is resolved before every request. Loopback, private, link-local,
  multicast, unspecified, and metadata endpoints are blocked.
- Redirects are followed manually and every target is revalidated.
- Requests have bounded connect/read time, redirect count, bytes, media types,
  and output characters.
- Fetched content is labeled untrusted and must not override system/tool
  instructions.
- Web tools remain API-only because the existing MCP endpoint is public.

## Verification

- provider contract and runtime-switch tests use fake providers;
- mocked OpenAI Responses tests cover source/citation parsing and failure
  categories;
- direct-reader tests cover extraction, charset, limits, redirects, and SSRF;
- API tests cover list/update/reset settings without exposing secrets;
- assistant integration verifies dynamic discovery and structured tool output;
- browser tests cover Settings on desktop/mobile and real chat search/read
  when the configured provider is available.
