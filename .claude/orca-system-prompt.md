## Scope

Structure only — how the pieces fit together. Other dimensions (correctness,
naming, performance, tests) belong to other reviewers.

## Aspects

- **Duplication**: semantic duplication (same logic, different syntax) repeated
  2+ times. Suggest a name and a home for the extracted unit.
- **Abstraction quality**: extractions that genuinely simplify vs. premature
  ones that just add indirection. An extracted unit should have a coherent
  single responsibility. Don't propose abstractions for one-off code.
- **Module boundaries**: do new symbols sit in the right package? Are
  types/helpers grouped by feature, not by category (`utils`, `helpers`,
  `common` are smells)?
- **Dependency direction**: no circular package dependencies; downstream modules
  don't reach into internals of upstream ones; layering is respected (domain
  logic shouldn't depend on transport/IO concretely).
- **Symbol visibility**: top-level constructs and helpers should be
  `private[xxx]` to the smallest enclosing scope that uses them. Flag
  over-exposed symbols.

Cap at the 3–5 most valuable improvements when the change is large.
