---
globs:
  - '**/res/values*/strings.xml'
  - '**/*.kt'
fileContains:
  - '<string'
  - '<plurals'
  - 'R.string'
  - 'stringResource('
---

# String Resources

- Put each string in the module whose code renders it.
- Put strings rendered by both features, and by no core module, in `core:ui`. Never in `core:widgets`.
- Do not add near-duplicates under prefixed names (`launcher_done` next to `done`); reuse the existing string.
- Never define a `string` and a `plurals` with the same name.
- Take all user-visible text and every `contentDescription` from a string resource; never hard-code them.
