# Morning Brief V2 Design

## Goal

Turn Morning Brief into a daily technology reading surface that discovers
current material without paid social APIs. Prefer first-party and public feeds,
use Firecrawl only for gaps and difficult pages, and make every run readable at
`/briefs` instead of hiding outputs in Settings history.

## Product Decisions

- Do not integrate X or OpenClaw as content sources.
- Do not scrape Reddit in V2. Reddit requires approved Data API access and
  OAuth; it can be added later through the same source contract.
- Discover through GitHub releases, RSS/Atom, Hacker News, and Bluesky public
  feeds. These sources run independently, and a partial outage does not fail a
  useful brief.
- Firecrawl Search is the bounded discovery fallback. Firecrawl Scrape reads
  only the final shortlisted URLs that need richer text.
- Firecrawl has a conservative per-run estimated credit budget. V2 does not use Agent, Crawl,
  JSON mode, enhanced proxy, or browser interaction.
- Source implementations are fixed code adapters, while repository names,
  feed URLs, Bluesky handles, topics, and exact searches are automation data.

## Runtime Flow

1. The handler asks all enabled source adapters to collect recent candidates
   concurrently.
2. Candidates are normalized to one contract with kind, title, URL, excerpt,
   source, author, publication time, and optional community score.
3. Canonical URLs remove tracking parameters and duplicates.
4. Ranking favors official releases, then named people/publishers, then
   community signals. Freshness and engagement break ties, while round-robin
   selection within each trust tier prevents one source from consuming the
   entire item budget.
5. The selected items render into deterministic Markdown grouped as Official,
   People & publishers, and Community radar.
6. The existing idempotent note output remains the durable artifact in the
   `Briefs` folder.

## Default Sources

- GitHub releases: `openai/codex`, `anthropics/claude-code`,
  `flutter/flutter`, `dart-lang/sdk`, `spring-projects/spring-ai`, and
  `facebook/react`.
- RSS/Atom: OpenAI News, Simon Willison, GitHub Blog, Spring Blog, React, and
  Inside Java.
- Bluesky: Boris Cherny, Simon Willison, Gergely Orosz, and Addy Osmani.
- Hacker News: top and best stories filtered by the configured topics.
- Firecrawl: exact user queries and topic-generated searches, capped by the
  configured credit budget.

## UI Direction

The page uses the existing shadcn/ui primitives. It borrows the useful control
plane patterns seen in Claw dashboards: visible source health, immediate run
history, and output artifacts. Its visual treatment remains Northstar: a calm
editorial reading desk, compact metadata, strong source hierarchy, and no
generic analytics-dashboard cards.

The reading surface follows Kibo UI's Blog Post and Typography patterns: one
centered article column with a compact issue selector above it. It deliberately
avoids a permanent archive/article split pane because Morning Brief is a daily
reading flow, not a rapid document-switching tool. Empty, loading, error, and
partial-source states are first-class.

Automation configuration uses a full-height shadcn Sheet rather than a long
modal. Schedule, Content, and Sources are separate tabs. Each source uses a
Switch and reveals its own full-width configuration only while enabled, so
repository names and feed URLs remain readable on desktop and mobile.

## Security And Cost Boundaries

- Firecrawl credentials stay in server environment configuration.
- Workflow JSON stores no secret.
- Only public HTTP(S) URLs are accepted.
- Firecrawl uses basic proxy and Markdown output only.
- Each run caps search count, result count, scrape count, concurrency, and a
  conservative credit estimate; actual usage is recorded from Firecrawl's
  response.
- Community content is labeled as a signal and never presented as a verified
  first-party release.
