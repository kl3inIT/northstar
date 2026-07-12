# 0028: Feature colors use semantic intent tokens

## Status

Accepted on 2026-07-12.

## Context

Northstar's shadcn primitives already used theme variables, but feature pages
repeated raw emerald, amber, sky, violet, and blue utilities for the same
states. The result remained dark/light compatible but made meaning inconsistent
and future theme changes expensive. Calendar and discipline hues are a separate
case because users rely on stable colors to distinguish categories.

## Decision

Feature intent colors use the theme-aware `info`, `success`, `warning`,
`insight`, and existing `destructive` tokens defined in `web/src/index.css`.
Components use those tokens for text, icons, active surfaces, borders, status
dots, callouts, and progress — never color alone.

Raw hue utilities are reserved for categorical data palettes. They live in a
central mapping module and are consumed by components; charts continue to use
`chart-1` through `chart-5`.

## Consequences

- Light and dark intent colors change from one source of truth.
- Finance, Study, Settings, Assistant tool states, Tasks, and Briefs share the
  same visual meaning.
- Calendar's third-party component appearance and user-selected colors remain
  unchanged while duplicate mappings can be centralized without visual churn.
- New feature code must classify a color as intent or data before adding it.
