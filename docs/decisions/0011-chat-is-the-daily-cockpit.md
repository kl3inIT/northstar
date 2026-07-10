# 0011 - Chat Is The Daily Cockpit

## Status

Accepted. A dedicated Today dashboard is deferred.

## Context

Northstar already exposes tasks, calendar, projects, finance, notes, and review
facts through Assistant tools. The user relies on chat to ask what matters
today and to follow up in context. A separate Today page would duplicate that
composition layer and create another surface to design, maintain, and keep
consistent with the underlying domains.

Domain pages still matter for scanning and correcting their own records. The
question is only whether Northstar also needs a fixed cross-domain dashboard.

## Decision

- Use Assistant/chat as the daily cockpit for composing tasks, events,
  milestones, finance signals, and review prompts.
- Do not add a `/today` route or a dedicated Today aggregation API now.
- Keep task, calendar, project, finance, and note pages focused on domain-level
  inspection and correction.
- Reconsider a Today surface only when evidence shows that chat latency,
  discoverability, or repeated prompting is a real problem, or when Northstar
  needs a zero-prompt glance/notification surface.

## Consequences

Northstar avoids two competing daily-start experiences and can invest in
Assistant reliability, tool quality, and stable streaming UX. Daily answers
remain conversational rather than fixed-layout, so important signals depend on
good tool selection and response quality. Any future Today page must justify
itself with a workflow chat cannot serve well, rather than merely restating the
same data.
