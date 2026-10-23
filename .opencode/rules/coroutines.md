---
globs:
  - '**/*.kt'
fileContains:
  - 'CoroutineScope'
  - 'stateIn('
  - 'shareIn('
  - 'callbackFlow'
  - '.launch'
  - '.collect'
---

# Coroutines and Flows

- Never construct a `CoroutineScope` in production code. Inject the app scope (`get(named(APP_SCOPE_QUALIFIER))`) or use `viewModelScope`.
- Use the one shared `WhileSubscribed` stop-timeout constant; never redeclare it or call `WhileSubscribed()` without it.
- Contain failures inside the `collect` lambda: rethrow `CancellationException`, log everything else with `Timber.e`, so the collector keeps running.
- Keep read flows free of side effects; writes and cleanup run in jobs owned by the repository.
- Register framework callbacks (`MediaController.registerCallback`, session listeners) with `Handler(Looper.getMainLooper())`.
- Do not add redundant `distinctUntilChanged()` or `flowOn(...)` when upstream already guarantees it.
- Never fall back silently: log with Timber when an expected value is missing.
