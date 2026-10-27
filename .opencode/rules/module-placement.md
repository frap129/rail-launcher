---
globs:
  - '**/*.kt'
  - '**/build.gradle.kts'
---

# Module Placement

- `core:data` holds repositories, Room and framework sources, and pure non-UI projections and helpers over repositories. No Compose and no Android UI.
- `core:ui` holds Compose UI, UI state, UI helpers and strings shared by both features, including the Pop-up collaborator layer. No ViewModels and no feature dependencies; `core:lifecycle` owns the Koin wiring that binds the collaborators (see AGENTS.md "Module layout").
- `core:lifecycle` holds application-level plumbing, application-scoped implementations and Koin wiring (including the bindings for the Pop-up collaborators), plus the activity-level building blocks (`MainApplication`, `TemplateActivity`, `HomeSignals`); only `app` depends on it, as the composition root.
- Code used by a single feature stays in that feature (e.g. `HomeEditState` in `feature:launcher`).
- Move shared types down into the core module that owns the concept; never share sideways between features.
- Declare every module a class uses as an explicit dependency; do not rely on transitive ones.
