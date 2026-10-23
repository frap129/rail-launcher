---
globs:
  - '**/*.kt'
  - '**/build.gradle.kts'
---

# Module Placement

- `core:data` holds repositories, Room and framework sources only. No UI helpers or in-memory UI state.
- `core:ui` holds shared Compose code and the UI helpers and state used by both features (e.g. `HomeHeightState`, `HomeInsets`).
- `core:widgets` holds widget code only. It is not a place to share feature code or resources.
- Code used by a single feature stays in that feature (e.g. `HomeEditState` in `feature:launcher`).
- Move shared types down into the core module that owns the concept; never share sideways between features.
- Declare every module a class uses as an explicit dependency; do not rely on transitive ones.
