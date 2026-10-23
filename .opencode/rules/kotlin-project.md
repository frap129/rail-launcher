---
globs:
  - '**/*.{kt,kts}'
---

# Kotlin in Rail

- Use `android.util.Log` only inside the Timber `ReleaseTree` sink in `MainApplication`; log through Timber everywhere else.
- Import types instead of writing inline fully-qualified names.
