---
keywords:
  - 'emulator'
  - 'adb'
  - 'logcat'
  - 'on device'
  - 'avd'
---

# Device Verification

- Run the emulator only through the flake: `nix develop .#emulator --command emulator -avd rail-api36`.
- Never install tools with `nix profile`/`nix-env` or run `sdkmanager`; if the flake lacks something, fix `flake.nix`.
- Clear logcat before reproducing a bug.
- Report a bug found only by reading code as "not reproduced".
