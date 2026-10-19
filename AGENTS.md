# AGENTS.md

Rail: Android home-screen launcher (Kotlin, Jetpack Compose, Koin, Room). Single Gradle build, JDK 17.

## Commands
- Build: `./gradlew :app:assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest` (JVM; coroutines-test + Turbine are wired by the convention plugins).
- Lint: `./gradlew ktlintCheck`; fix: `./gradlew ktlintFormat` (CI runs `ktlintFormat` on push to `main` and auto-commits `ci(autoformat): ktlint`).
- No instrumented tests yet; verification = tests + build + ktlint.
- Docs: `./gradlew generateDocs` regenerates Dokka output into `docs/html` and `docs/markdown`. These are generated (and currently stale); never hand-edit them.
- Baseline profiles: `./gradlew :app:generateBaselineProfile` (Gradle managed device `pixel6Api34`, needs emulator support). Output is saved into `src/main/baseline-prof.txt` per module and `app/src/main/generated/baselineProfiles/`; do not hand-edit.

## Module layout
- `app` (`dev.maples.rail`): only `MainActivity`, which lists the nav `Destination`s (`launcherDestination`, `settingsDestination`) passed to `core.ui.App`.
- `core:lifecycle`: `MainApplication` (set in app manifest) owns all Koin wiring (DAOs, repositories, ViewModels) and the Room DB. New repos/ViewModels must be registered here; it depends on the feature modules for that reason.
- `core:data`: repositories as `IFoo` interface + `IFooImpl` class, Room DB (`AppDatabase`, KSP), DataStore prefs.
- `core:ui` (shared Compose, theme, `ScrollRail`, `Destination` model), `core:util`, `feature:launcher`, `feature:settings`.
- Package names are module paths (`core.data`, `feature.launcher`), not `dev.maples.*`, except `app`.
- `core:baselineprofile` is a `com.android.test` module with its own hard-coded SDK/Java 11 config; don't apply convention plugins to it.

## Build conventions
- New modules: use convention plugins from `build-logic/` via catalog aliases `libs.plugins.modular.android.library` / `.application` and `libs.plugins.modular.compose`, then `include(...)` in `settings.gradle.kts`.
- Convention plugins already add compileSdk 35 / minSdk 32, coroutines, lifecycle, Koin, Timber, baseline profile wiring, and (compose plugin) Compose BOM, Material3, Navigation, Coil, Koin-Compose. Don't re-declare these in module build files.
- All dependency versions go through `gradle/libs.versions.toml`.
- Optional release signing via untracked root `keystore.properties`.

## Style
- ktlint `android_studio` style with experimental rules, max line 140, no wildcard imports (`.editorconfig`).
- Logging via Timber, not `Log`.

## Git / PRs
- Conventional Commits with module scopes, e.g. `feat(core:data): ...`, `refactor(launcher): ...`.
- PRs are squash-merged; PR title becomes the commit subject: allowed types `build ci docs feat fix perf refactor style test chore revert`, max 50 chars (72 with `allow-long-subject` label), no trailing punctuation.
- PR body above `****` becomes the commit body: lines <= 72 chars and must contain a `ticket: ` trailer (see `.github/pull_request_template.md`).

## Agent skills

### Issue tracker

Issues are tracked with beads (`bd` CLI, prefix `rail`, stored in `.beads/`). See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary as beads labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: root `GLOSSARY.md` + `docs/adr/`. See `docs/agents/domain.md`.
