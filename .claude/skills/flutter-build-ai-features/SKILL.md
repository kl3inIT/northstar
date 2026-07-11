---
name: flutter-build-ai-features
description: Design, implement, review, and test reliable AI-powered features in Flutter apps. Use when work involves LLM prompts, structured input or output, tool/function calls, ask-versus-agent decisions, human confirmation, confidence handling, AI error states, or guardrails around nondeterministic model behavior.
---

# Build AI Features in Flutter

Treat model output as untrusted, nondeterministic input. Keep deterministic
application behavior in code and use the model only where language, vision, or
creative reasoning provides material value.

## Workflow

1. Define the user outcome and the failure that must not happen.
2. Decide whether normal Dart code can solve the task predictably. Prefer code
   for validation, state transitions, permissions, retries, and task tracking.
3. Choose the least-powerful interaction mode:
   - use **ask mode** for generation or read-only lookup;
   - use **agent mode** only when the model must act through tools.
4. Layer prompts:
   - put durable role, policy, and tool guidance in the system instruction;
   - put task-specific context, constraints, and requested format in the user
     prompt;
   - pass only the context required for the current decision;
   - keep production prompts versioned separately from UI code.
5. Define typed input and output contracts. Prefer provider-enforced schemas,
   validate every field after decoding, and map transport DTOs to domain models.
6. Keep the tool set small, targeted, and non-overlapping. Give every tool a
   precise name, purpose, argument schema, and conditions for use. Validate
   arguments and authorization in deterministic code.
7. Add guardrails before exposing the feature:
   - timeouts, cancellation, bounded tool iterations, and typed failures;
   - user confirmation before destructive, external, financial, or privacy-
     sensitive actions;
   - editable previews when model-generated data affects saved state;
   - safe fallback UI and retry behavior;
   - telemetry that excludes secrets and sensitive prompt content.
8. Keep Flutter layers separate: widgets render state, ViewModels coordinate
   interactions, repositories expose domain results, and services handle the
   backend transport. Never call a model provider directly from `build()`.
9. Test deterministic code with unit/widget tests. Test prompt and tool behavior
   with representative real-model evaluations plus mocked tool handlers.

## Northstar Boundary

- Route mobile AI requests through the Northstar backend. Do not embed provider
  API keys in the Flutter app or add a direct Firebase/Genkit/model SDK unless
  the user explicitly changes the architecture.
- Keep authorization, provider selection, model orchestration, and server-side
  tools in the backend. Keep presentation, confirmation, correction, and
  recoverable client state in Flutter.
- Reuse the backend's typed contracts rather than creating a second mobile-only
  interpretation of assistant behavior.

## Verification

Run, at minimum:

```powershell
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
```

Add widget tests for loading, partial output, correction, confirmation, empty,
error, timeout, and retry states. For an agentic feature, test denied actions,
invalid tool arguments, repeated tool calls, and loop termination.

Read [official-best-practices.md](references/official-best-practices.md) when
choosing prompt structure, output schemas, tools, or human-in-the-loop behavior.
