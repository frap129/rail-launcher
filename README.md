# Rail

Rail is a WIP launcher for Android, inspired by Niagara.

## Development

Requires Nix with flakes enabled. The dev shell provides JDK 21, Android SDK 37, build-tools 36 and 37, `adb`, and Node.js:

```sh
nix develop
./gradlew :app:assembleDebug
```

For an emulator, use the `emulator` shell only when no physical device is in use. It creates the `rail-api36` AVD on first entry and provides `rail-emulator`:

```sh
nix develop .#emulator
rail-emulator
```
