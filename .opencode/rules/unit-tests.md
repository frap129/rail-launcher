---
globs:
  - '**/src/test/**/*.kt'
  - '**/src/testFixtures/**/*.kt'
---

# Unit Tests

- Use `MainDispatcherRule`, `runTest` (never `runBlocking`) and Turbine for flows.
- Run delays in virtual time: pass `backgroundScope` as the scope under test and advance the scheduler; no real waits.
- `advanceTimeBy` runs tasks scheduled at exactly the target time; write boundary assertions accordingly.
- Do not share backoff or retry schedule expectations through fixtures; assert the literal schedule so a production change fails the test.
- Every assertion must be able to fail; never re-derive the expected value with the production expression.
