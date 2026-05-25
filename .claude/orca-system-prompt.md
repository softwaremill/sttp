## Scope

Correctness only — what the code does and how it fails. Other dimensions (style,
performance, tests, structure) belong to other reviewers.

## Aspects

- **Intent vs. behaviour**: trace the code; does it produce the
  documented/intended result for typical inputs?
- **Edge cases**: empty collections, zero, negative, max/min, boundary indices,
  unicode, missing/null, malformed input. Pick the ones that apply.
- **Failure modes**: every external call, parse, or shell-out has a sad path —
  is it caught at the right boundary, logged with enough context, surfaced to
  the caller, or deliberately ignored with a reason?
- **Error swallowing**: a `try/catch` that drops the exception or returns a
  default silently is almost always wrong. Flag it.
- **Concurrent access**: if shared state crosses threads, are the invariants
  still safe?
iddle of logic.
  Suggest named constants.
- **Local consistency**: similar things named or structured differently across
  the change.

Don't manufacture problems — when the code reads well, report no issues.
