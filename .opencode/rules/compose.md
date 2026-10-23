---
globs:
  - '**/*.kt'
fileContains: '@Composable'
---

# Compose

- Wrap one-time creation in `remember(key) { ... }`; never call a create or get-or-create function directly in the composable body.
- Key `pointerInput` on the state its block reads, not `Unit`.
- Take shared dimensions (touch target size, `homeEdgePadding`, `homeRailTouchInset`) from `core:ui`; never redeclare them locally.
- Trigger haptics through `core/ui/.../util/Haptics.kt`.
- Hide non-visible content from accessibility services with `clearAndSetSemantics`, and give every clickable region a content label.
- Add a `testTag` only when a test or benchmark reads it.
