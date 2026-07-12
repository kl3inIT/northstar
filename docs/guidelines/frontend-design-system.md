# Frontend Design System

Northstar uses shadcn/ui primitives with Tailwind v4 theme variables. Color is
assigned by meaning so dark/light themes and future brand changes do not require
feature-by-feature palette rewrites.

## Semantic color contract

`web/src/index.css` is the source of truth. In addition to shadcn's base tokens,
Northstar defines four app intents:

| Token | Meaning | Typical use |
| --- | --- | --- |
| `info` | external/live/informational context | provider tabs, live-feed selection, informational callouts |
| `success` | ready, healthy, complete, positive movement | connected state, income, completed progress, successful runs |
| `warning` | attention, freshness risk, exceptional or at-risk state | stale data, upcoming charge, weak recall, trending/highlight |
| `insight` | AI-derived or app-curated intelligence | Northstar Brief, estimates, generated insight summaries |

`destructive` remains the error/danger intent. `primary` remains the main brand
action; `muted`, `secondary`, and `accent` remain neutral hierarchy/surface
tokens. Use opacity for tone variants: `bg-warning/10`, `border-success/40`,
`text-info`. Do not add separate light/dark palette classes for these intents;
the CSS variables already adapt to the theme.

Color is not limited to buttons and badges. Apply the same intent to the
smallest useful combination of icon, active tab/toggle surface, status dot,
progress indicator, callout border/background, inline link, or selected row.
Text remains the primary carrier of meaning; color is reinforcement and must
not be the only state signal.

## Data palettes are different

User-selectable or categorical colors are not status intents. Calendar event
colors and discipline colors keep stable named hues so separate categories stay
visually distinguishable. Their raw Tailwind palette classes are allowed only
inside centralized mapping modules such as
`features/calendar/calendar-color-tokens.ts` and `lib/discipline-colors.ts`.
Feature components consume those mappings instead of repeating palette names.
Charts use `chart-1` through `chart-5`.

## Review rule

Before adding `blue-*`, `emerald-*`, `amber-*`, `violet-*`, or another raw hue
to a feature component, decide whether the color is an intent or categorical
data. Use a semantic token for intent; add or reuse one centralized mapping for
categorical data. Raw palette classes must not be scattered through pages or
feature components. Preserve readable contrast in both themes and keep icons,
labels, and state text available for non-color perception.
