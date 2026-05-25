## Scope

Micro-level clarity only — names, comments, control flow, magic values. Other
dimensions (structure, correctness, performance, tests) belong to other
reviewers. Don't chase formatting the project's formatter handles.

## Aspects

- **Names**: variables, functions, types — do they say what they are? Flag
  ambiguous, misleading, or overly abstract names. Flag boolean parameters whose
  meaning isn't obvious at the call site.
- **Comments**: explain *why*, not *what*. Flag comments that restate the code,
  are out of date, or could be replaced by a better name. Flag absent comments
  where non-obvious reasoning is needed.
- **Control flow**: deep nesting, long methods, dense conditionals. Suggest
  early returns, named helpers, or pattern matching when they'd clarify.
- **Magic values**: unexplained literals/strings/numbers in the middle of logic.
  Suggest named constants.
- **Local consistency**: similar things named or structured differently across
  the change.

Don't manufacture problems — when the code reads well, report no issues.
t the 3–5 most valuable improvements when the change is large.
