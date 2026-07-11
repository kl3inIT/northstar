# Cupertino Mobile Shell

## Problem

The Flutter foundation can render a platform-selected hello screen but does not
yet provide a reusable design system, real information architecture, or a shell
that can support Northstar's product flows. Development happens on Windows, so
the Cupertino experience must also be previewable and testable on Web without
pretending that browser platform detection is an iPhone.

## Product Direction

Northstar mobile is Cupertino-first for this increment. The app uses Cupertino
controls on every target so the iPhone experience can be developed and reviewed
from Windows. Platform adaptation can return later behind a deliberate design
policy; it is not mixed into the first production shell.

The compact navigation has five destinations:

1. Assistant
2. Tasks
3. Notes
4. Finance
5. More

Calendar, Projects, Disciplines, and Settings live under More. Capture is a
primary Assistant action rather than a permanent tab.

## Design System

- Prefer iOS semantic and dynamic colors so light/dark and accessibility
  contrast follow the host system.
- Centralize color, spacing, radius, typography, and content-width decisions.
- Use native Cupertino navigation, tab, button, icon, switch, and text-field
  behavior where available.
- Keep touch targets at least 44 logical pixels and support text scaling.
- Constrain readable content on expanded windows instead of stretching it.
- Represent loading, empty, error, and disabled states in reusable components.

Penpot is optional. It becomes useful for collaborative high-fidelity review or
design handoff, but the first source of truth is executable widgets plus previews
and tests.

## Architecture

`ui/core/design_system/` owns tokens, themes, and shared components.
`ui/core/navigation/` owns destination metadata, navigation state, and the
adaptive shell. Views receive state through constructors or Listenable-backed
controllers. No API, persistence, or model-provider dependency is introduced in
this increment.

Compact windows use `CupertinoTabScaffold`. Windows at least 600 logical pixels
wide use a Cupertino-styled sidebar and constrained detail region. Both layouts
share one destination model and one controller so selection survives resizing.

## Scope

- Cupertino-only root app for the current development phase;
- semantic design tokens and shared surface/empty-state components;
- five-destination compact tab shell and expanded sidebar shell;
- an Assistant landing surface and honest placeholder states for unfinished
  product areas;
- isolated widget previews;
- widget tests for navigation and compact/expanded behavior.

Authentication, REST/OpenAPI integration, persisted state, production feature
screens, and iOS signing are outside this increment.

## Assistant Library Evaluation

Flutter AI Toolkit 1.0.0 provides a capable chat view, streaming, attachments,
voice input, function calls, and a custom provider interface. It is not adopted
for this shell: its current examples and dependency graph are Material/Firebase-
oriented, while Northstar needs a Cupertino-owned presentation layer over its
existing backend assistant. Revisit the toolkit only if its UI primitives can be
used without introducing direct provider calls, Firebase ownership, or a second
design system in the mobile client.

## Verification

- `dart format --output=none --set-exit-if-changed lib test`;
- `flutter analyze`;
- widget tests at compact and expanded widths;
- `flutter build web --release` so the Cupertino shell remains reviewable from
  Windows.
