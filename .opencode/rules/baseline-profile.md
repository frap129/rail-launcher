---
globs:
  - 'core/baselineprofile/**/*.kt'
---

# Baseline Profile Journeys

- Assert state before and after each step instead of acting blindly (e.g. read a switch, toggle only if needed, assert the result; wait with `Until.gone(...)` and check it).
- Find required nodes with `findOrThrow` and drive gestures through the shared helpers (`dragScrollRail()`); never duplicate a gesture.
- Hoist fling, drag and scroll values into named constants.
- Match UI text through string resources, not copied literals.
