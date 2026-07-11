# Official Flutter AI Best Practices

These notes distill the Flutter 3.44 documentation. Re-check the linked pages
before implementing provider-specific APIs because SDK details can change.

## Source Map

- Overview and guardrails:
  <https://docs.flutter.dev/ai/best-practices>
- Prompt construction, layering, parameterization, and versioning:
  <https://docs.flutter.dev/ai/best-practices/prompting>
- Structured input and output:
  <https://docs.flutter.dev/ai/best-practices/structure-output>
- Tool/function calls and human-in-the-loop flows:
  <https://docs.flutter.dev/ai/best-practices/tool-calls>
- Code versus LLM and ask versus agent decisions:
  <https://docs.flutter.dev/ai/best-practices/mode-of-interaction>

## Design Checklist

### Suitability and interaction mode

- Use deterministic Dart code when the behavior can reasonably be implemented
  and tested as code.
- Use an LLM for language, vision, ambiguity, or creative problem solving where
  nondeterminism is an acceptable tradeoff.
- Treat model data like user input: validate, constrain, correct, and test it.
- Prefer ask/read-only behavior. Add action tools only when the user outcome
  requires them.

### Prompts

- Include a focused role, relevant context, one clear task, constraints, and an
  explicit output format.
- Keep stable rules in the system instruction and dynamic task data in the
  request prompt.
- Reduce context to the smallest representation that preserves the decision.
- Parameterize prompts and version production prompts outside widget code.

### Structured data

- Preserve structure in inputs instead of flattening data into vague prose.
- Request a provider-enforced output schema and JSON MIME type when supported.
- Repeat critical schema expectations in the instruction when needed.
- Successful parsing does not imply factual correctness; apply domain
  validation and offer correction for consequential data.

### Tools and agents

- Use a small set of targeted, non-overlapping tools.
- Define tool name, description, argument schema, and when to use or avoid it.
- Execute tools in application code, validate their arguments, return typed
  results, and reject unknown tool names.
- Keep deterministic progress/state tracking out of the model loop.
- Put a human in the loop when the model encounters a conflict or proposes a
  consequential action.

### Evaluation

- Build unit and widget tests around parsing, validation, state, and handlers.
- Exercise real models with varied representative prompts while mocking tools.
- Use failures to add targeted guardrails rather than relying on prompt wording
  alone.
