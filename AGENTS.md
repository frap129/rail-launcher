# AGENTS.md

Rail: Android home-screen launcher (Kotlin, Jetpack Compose, Koin, Room). Single Gradle build, JDK 21.

## Commands

All development commands must run inside the Nix flake. Do not export toolchain variables or use host JDK/Android SDK installations; use `nix develop --command <command>` for one-off commands, or enter an interactive shell with `nix develop`.

- Build: `nix develop --command ./gradlew :app:assembleDebug`
- Unit tests: `nix develop --command ./gradlew testDebugUnitTest` (JVM; JUnit, coroutines-test, Turbine, MockK and Robolectric are wired by the convention plugins; tests use MockK mocks, and data-layer tests use in-memory Room via Robolectric).
- Lint: run `nix develop --command ./gradlew ktlintFormat` before `nix develop --command ./gradlew ktlintCheck` so autofixable formatting issues are resolved first (CI runs `ktlintFormat` on push to `main` and auto-commits `ci(autoformat): ktlint`).
- No `androidTest` in app/feature/core modules (only `core:baselineprofile`'s benchmark harness); verification = tests + build + ktlint.
- Docs: `nix develop --command ./gradlew generateDocs` regenerates Dokka output into `docs/html` and `docs/markdown`. These are generated (and currently stale); never hand-edit them.
- Baseline profiles: `nix develop .#emulator --command ./gradlew generateBaselineProfile` (Gradle managed device `pixel6Api36`; only the `.#emulator` shell's SDK has the emulator and system image it needs). Use the root task: it regenerates `app/src/main/generated/baselineProfiles/` and every module's `src/main/generated/baselineProfiles/`, while `:app:generateBaselineProfile` refreshes only the app's files. Do not hand-edit them.
- Emulators: the Android MCP's `start_emulator` tool is denied in `opencode.json`. Start an AVD manually with `nix develop .#emulator --command emulator -avd <name>` only when no physical device is in use.

## Module layout
- Modules are layered App > Feature > Core. A module may depend on modules in its own layer or below, never above: core modules never depend on feature modules or `app`, and feature modules never depend on `app`.
- Place code by kind, not by how many features use it today: UI components, UI state, UI helpers and their strings go in `core:ui`, data code in `core:data`, non-UI utilities in `core:util`. A feature module keeps only what is specific to that feature: its ViewModels, its screens and `Destination`, and the glue binding its state to core components. Abstract into core from the start.
- `app` (`dev.maples.rail`): a thin `MainActivity` that lists the nav `Destination`s (`launcherDestination`, `settingsDestination`) passed to `core.ui.App`, plus `SystemBarIconContrast` (system-bar icon-tone decision logic, unit-tested).
- `core:lifecycle`: application-level plumbing: `MainApplication` (installed by the app manifest) owns all Koin wiring (DAOs, repositories, ViewModels, Pop-up collaborators) and the Room DB, and the module also holds application-scoped implementations, the `TemplateActivity` base, and `LauncherSignals`, the activity-owned launcher-signal channel exposed as a `Flow`. New repos, ViewModels and Pop-up collaborator bindings must be registered here. Its `MainApplication` currently imports feature types, which breaks the layering above; `rail-aj7` moves the composition root to `app`. Until then, add no other feature imports to this module.
- `core:data`: repositories as `IFoo` interface + `IFooImpl` class, Room DB (`AppDatabase`, KSP), framework sources, and pure non-UI projections and helpers over repositories. No Compose and no Android UI. No DataStore; preferences are Room-backed. Room schemas export to `core/data/schemas`, so commit the new JSON on every `@Database` version bump.
- `core:ui`: Compose UI, UI state, UI helpers and strings (theme, `ScrollRail`, `Destination` model), plus the Pop-up collaborator layer in `core.ui.popups`: the `IPopupSession` contract, the host-implemented `IPopupCard` contract, the `IPopupCollaborators` aggregate, the editor, the Add apps and Shortcut pickers, and the Folder actions. These are UI collaborators, not ViewModels; `core:lifecycle` owns the Koin wiring that binds them. No ViewModels and no feature dependencies.
- `core:util`, `feature:launcher`, `feature:settings`: non-UI utilities and the feature modules. Existing feature modules still hold UI code that belongs in core; `rail-aj7` moves it. Put new code where it belongs.
- Feature modules must not depend on other feature modules; the Android library convention plugin fails the build when one declares such a dependency.
- Package names are module paths (`core.data`, `feature.launcher`), not `dev.maples.*`, except `app`.
- `core:baselineprofile` is a `com.android.test` module with its own hard-coded SDK/Java 11 config; don't apply convention plugins to it.

## Build conventions
- New modules: use convention plugins from `build-logic/` via catalog aliases `libs.plugins.modular.android.library` / `.application` and `libs.plugins.modular.compose`, then `include(...)` in `settings.gradle.kts`.
- Convention plugins already add compileSdk 37 / minSdk 32, coroutines, lifecycle, Koin, Timber, baseline profile wiring, and (compose plugin) Compose BOM, Material3, Navigation, Coil, Koin-Compose. Don't re-declare these in module build files. They do not set `targetSdk`, so application modules must set it (`app` uses 37).
- All dependency versions go through `gradle/libs.versions.toml`.
- Optional release signing via untracked root `keystore.properties`.

## Style
- ktlint `android_studio` style with experimental rules, max line 140, no wildcard imports (`.editorconfig`).
- Logging via Timber, not `Log`.
- Duplicated code or logic is never acceptable, however small. Before adding code, search for an existing implementation and reuse or extract it; this covers constants, strings, resources, helpers, state transitions and test setup. Fix every duplication a review finds unless a later ticket already covers it.
- Always delete superseded code rather than preserving it; never add no-op code or guards unless explicitly required.

## Git / PRs
- Conventional Commits with module scopes, e.g. `feat(core:data): ...`, `refactor(launcher): ...`.
- PRs are rebase-merged: every commit lands on `main` as written, so each commit subject and body must meet the rules below on its own.
- Commit subjects and PR titles: allowed types `build ci docs feat fix perf refactor style test chore revert`, max 50 chars (72 with `allow-long-subject` label), no trailing punctuation. The PR linter checks the title.
- Commit bodies and the PR body above `****`: lines <= 72 chars and a `ticket: ` trailer (see `.github/pull_request_template.md`). The PR linter checks the PR body.
- PR bodies always follow `.github/pull_request_template.md`: keep its Testing Steps, Visuals and Reviewer Section below `****`, and never replace them with other sections.
- Commit scopes name a real module (`core:ui`, `launcher`), never a feature area such as `widgets`.

### Ticket delivery

- One commit per ticket: amend review fixes into that commit instead of adding new ones.
- Close a ticket (`bd close <id> --reason "<summary>"`) as soon as its work is committed and verified. Do not push unless asked.
- Verify in order: `testDebugUnitTest` (with `--rerun` on the touched modules), `:app:assembleDebug`, `ktlintFormat`, then `ktlintCheck`.

## Agent skills

### Issue tracker

Issues are tracked with beads (`bd` CLI, prefix `rail`, stored in `.beads/`). See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary as beads labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: root `GLOSSARY.md` + `docs/adr/`. See `docs/agents/domain.md`.
