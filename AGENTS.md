# AGENTS.md

Rail: Android home-screen launcher (Kotlin, Jetpack Compose, Koin, Room). Single Gradle build, JDK 21.

## Commands

All development commands must run inside the Nix flake. Do not export toolchain variables or use host JDK/Android SDK installations; use `nix develop --command <command>` for one-off commands, or enter an interactive shell with `nix develop`.

- Build: `nix develop --command ./gradlew :app:assembleDebug`
- Unit tests: `nix develop --command ./gradlew testDebugUnitTest` (JVM; coroutines-test + Turbine are wired by the convention plugins).
- Lint: run `nix develop --command ./gradlew ktlintFormat` before `nix develop --command ./gradlew ktlintCheck` so autofixable formatting issues are resolved first (CI runs `ktlintFormat` on push to `main` and auto-commits `ci(autoformat): ktlint`).
- No `androidTest` in app/feature/core modules (only `core:baselineprofile`'s benchmark harness); verification = tests + build + ktlint.
- Docs: `nix develop --command ./gradlew generateDocs` regenerates Dokka output into `docs/html` and `docs/markdown`. These are generated (and currently stale); never hand-edit them.
- Baseline profiles: `nix develop --command ./gradlew :app:generateBaselineProfile` (Gradle managed device `pixel6Api36`, needs emulator support). Output is saved into `src/main/generated/baselineProfiles/` per module and `app/src/main/generated/baselineProfiles/`; do not hand-edit.
- Emulators: the Android MCP's `start_emulator` tool is denied in `opencode.json`. Start an AVD manually with `nix develop .#emulator --command emulator -avd <name>` only when no physical device is in use.

## Module layout
- `app` (`dev.maples.rail`): a thin `MainActivity` that lists the nav `Destination`s (`launcherDestination`, `settingsDestination`) passed to `core.ui.App`, plus `SystemBarIconContrast` (system-bar icon-tone decision logic, unit-tested).
- `core:lifecycle`: `MainApplication` (set in app manifest) owns all Koin wiring (DAOs, repositories, ViewModels) and the Room DB. New repos/ViewModels must be registered here; it depends on the feature modules for that reason. Also `HomeSignals`, the activity-owned HOME-press channel exposed as a `Flow`.
- `core:data`: repositories as `IFoo` interface + `IFooImpl` class, Room DB (`AppDatabase`, KSP). No DataStore; preferences are Room-backed. Room schemas export to `core/data/schemas`, so commit the new JSON on every `@Database` version bump.
- `core:widgets`: the presentation slice shared by both features: Header rendering/formatting, the clock-style chooser, the Move Widget Area slider, the notification-access dialog, the App Widget host view cache, picker and content, the add/resize controllers, and the widget event relay.
- `core:popups`: the Pop-up card, swipe gesture, placement maths, shortcut content and `PopupViewModel`; shared by the launcher and (later) settings.
- `core:ui` (shared Compose, theme, `ScrollRail`, `Destination` model), `core:util`, `feature:launcher`, `feature:settings`.
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

## Git / PRs
- Conventional Commits with module scopes, e.g. `feat(core:data): ...`, `refactor(launcher): ...`.
- PRs are squash-merged; PR title becomes the commit subject: allowed types `build ci docs feat fix perf refactor style test chore revert`, max 50 chars (72 with `allow-long-subject` label), no trailing punctuation.
- PR body above `****` becomes the commit body: lines <= 72 chars and must contain a `ticket: ` trailer (see `.github/pull_request_template.md`).
- Commit scopes name a real module (`core:widgets`, `launcher`), never a feature area such as `widgets`.

### Ticket delivery

- One commit per ticket: amend review fixes into that commit instead of adding new ones.
- Do not push and do not close tickets unless asked.
- Verify in order: `testDebugUnitTest` (with `--rerun` on the touched modules), `:app:assembleDebug`, `ktlintFormat`, then `ktlintCheck`.

## Agent skills

### Issue tracker

Issues are tracked with beads (`bd` CLI, prefix `rail`, stored in `.beads/`). See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary as beads labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: root `GLOSSARY.md` + `docs/adr/`. See `docs/agents/domain.md`.
