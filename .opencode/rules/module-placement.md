---
globs:
  - '**/*.kt'
  - '**/build.gradle.kts'
---

# Module Placement

- Layers, top to bottom: `app`; `core:lifecycle`, the composition root; `feature:launcher` and `feature:settings`; `core:ui`, `core:data` and `core:util`.
  A module may depend only on modules in its own layer or below.
- `core:ui`, `core:data` and `core:util` never depend on a feature module, `core:lifecycle` or `app`. Feature modules never depend on `app`, `core:lifecycle` or another feature module.
  Only `app` depends on `core:lifecycle`, which names feature types only to bind them in Koin.
- Place code by kind, not by how many features use it today.
- A feature module keeps only:
  - its ViewModels, and the state and event types only they and the feature's screens use;
  - its Destinations;
  - its screens with their private composables: `LauncherScreen` and `LauncherContent`, the Home, App List and Search content
    (`LauncherHome`, `LauncherAppList`, `LauncherSearch`), and each Settings screen;
  - glue, meaning any composable or helper that takes one of its ViewModels or the state types it keeps.
- Everything else moves by kind: composables, modifiers, gestures, UI state and UI helpers to `core:ui`; non-UI utilities to `core:util`;
  non-UI helpers over `core:data` types to `core:data`. Strings go with the code that renders them.
- `core:data` holds repositories, Room and framework sources, and pure non-UI projections and helpers over repositories. No Compose and no Android UI.
- `core:ui` holds Compose UI, UI state, UI helpers and strings, including the Pop-up collaborator layer. No ViewModels and no feature dependencies;
  `core:lifecycle` owns the Koin wiring that binds the collaborators (see AGENTS.md "Module layout").
- `core:lifecycle` holds application-level plumbing, application-scoped implementations and Koin wiring (including the bindings for the Pop-up collaborators),
  plus the activity-level building blocks (`MainApplication`, `TemplateActivity`, `LauncherSignals`). It is the composition root, and only `app` depends on it.
- Declare every module a class uses as an explicit dependency; do not rely on transitive ones.
