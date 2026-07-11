# Flutter and Dart AI Rules for Northstar

Adapted for this repository from Flutter's official `rules_10k.md`:
<https://github.com/flutter/flutter/blob/master/docs/rules/rules_10k.md>.

Apply these rules to work under `mobile/`. Repository instructions in
`CLAUDE.md`, architecture decisions, and explicit user requirements take
precedence over generic Flutter guidance.

## Tooling

- Use the Dart and Flutter MCP server for symbol lookup, analysis, dependency
  search, tests, runtime errors, and widget-tree inspection when it is available.
- Fall back to `dart format`, `flutter analyze`, and `flutter test` when MCP is
  unavailable.
- Add packages with `flutter pub add`; explain why each dependency is needed and
  prefer well-maintained packages from pub.dev.
- Keep `flutter_lints` enabled and do not silence analyzer errors to finish a
  task.

## Dart and Flutter Code

- Write sound null-safe Dart. Avoid `!` unless the invariant is demonstrated.
- Prefer immutable data, `const` widgets, exhaustive switches, pattern matching,
  descriptive names, and small single-purpose functions.
- Prefer composition over inheritance and small widget classes over helper
  methods that return widgets.
- Keep network calls, parsing, storage, and expensive computation out of
  `build()` methods.
- Use lazy builders for long lists and move expensive computation off the UI
  isolate when profiling shows it is necessary.
- Handle failures explicitly and use structured logging rather than `print`.

## Architecture

- Separate UI, ViewModel/application logic, domain models, repositories, and
  external services.
- Start with constructor injection and built-in `Listenable` APIs. Add state or
  dependency-injection packages only when the project requirement justifies
  them.
- Keep views lean and make state snapshots immutable.
- Add data/domain folders only when real behavior needs them; do not create
  placeholder layers.

## Platform and Layout

- Preserve Northstar's injected design policy: Cupertino controls on iOS and
  Material controls on other targets.
- Make layout decisions from available constraints, not platform labels. Check
  compact and expanded widths for responsive changes.
- Support text scaling, semantics, keyboard/focus behavior where applicable,
  sufficient contrast, loading/error states, and touch targets.
- Do not replace Cupertino controls with a Material-only design unless the user
  explicitly changes the product direction.

## AI-Powered Features

- Use `$flutter-build-ai-features` for prompts, schemas, tool calls, agent loops,
  model-generated data, or human-in-the-loop flows.
- Treat model output as untrusted input. Validate it and provide correction or
  confirmation for consequential actions.
- Keep provider secrets and model orchestration on the Northstar backend, not in
  the Flutter client.

## Verification

- Format every changed Dart file.
- Run `flutter analyze` and relevant unit/widget tests.
- For UI changes, verify the actual state or flow at relevant widths and report
  any platform build that cannot run on Windows.
