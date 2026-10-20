{
  description = "Rail Android development environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

  outputs =
    { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" ];
      forAllSystems = nixpkgs.lib.genAttrs systems;

      pkgsFor =
        system:
        import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };

      buildAndroid =
        pkgs:
        pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "36" ];
          buildToolsVersions = [ "36.0.0" ];
          includeCmake = false;
          includeNDK = false;
          includeSources = false;
        };

      emulatorAndroid =
        pkgs:
        pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "36" ];
          buildToolsVersions = [ "36.0.0" ];
          includeEmulator = true;
          includeSystemImages = true;
          systemImageTypes = [ "default" ];
          abiVersions = [ "x86_64" ];
          includeCmake = false;
          includeNDK = false;
          includeSources = false;
        };

      shellFor =
        {
          pkgs,
          sdk,
          withEmulator,
        }:
        pkgs.mkShell {
          packages = [
            pkgs.jdk21
            sdk
            pkgs.nodejs
          ];
          JAVA_HOME = "${pkgs.jdk21}";
          ANDROID_HOME = "${sdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${sdk}/libexec/android-sdk";
          shellHook = ''
            export PATH="$ANDROID_HOME/${if withEmulator then "emulator:" else ""}platform-tools:$PATH"
          ''
          + nixpkgs.lib.optionalString withEmulator ''
            if ! avdmanager list avd 2>/dev/null | grep -q 'Name: rail-api36'; then
              echo no | avdmanager create avd -n rail-api36 -k "system-images;android-36;default;x86_64" -d pixel_6
            fi

            rail-emulator() {
              emulator -avd rail-api36 -no-snapshot -no-boot-anim "$@"
            }
          '';
        };
    in
    {
      devShells = forAllSystems (
        system:
        let
          pkgs = pkgsFor system;
        in
        {
          default = shellFor {
            inherit pkgs;
            sdk = (buildAndroid pkgs).androidsdk;
            withEmulator = false;
          };
          emulator = shellFor {
            inherit pkgs;
            sdk = (emulatorAndroid pkgs).androidsdk;
            withEmulator = true;
          };
        }
      );

      formatter = forAllSystems (system: (pkgsFor system).nixfmt);
    };
}
