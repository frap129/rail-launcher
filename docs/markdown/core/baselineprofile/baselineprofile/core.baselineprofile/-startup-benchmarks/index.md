//[baselineprofile](../../../index.md)/[core.baselineprofile](../index.md)/[StartupBenchmarks](index.md)

# StartupBenchmarks

[main]\
class [StartupBenchmarks](index.md)

This test class benchmarks the speed of app startup. Run this benchmark to verify how effective a Baseline Profile is. It does this by comparing CompilationMode.None, which represents the app with no Baseline Profiles optimizations, and CompilationMode.Partial, which uses Baseline Profiles.

Run this benchmark to see startup measurements and captured system traces for verifying the effectiveness of your Baseline Profiles. You can run it directly from Android Studio as an instrumentation test, or run all benchmarks for a variant, for example benchmarkRelease, with this Gradle task:

```kotlin
./gradlew :core:baselineprofile:connectedBenchmarkReleaseAndroidTest
```

You should run the benchmarks on a physical device, not an Android emulator, because the emulator doesn't represent real world performance and shares system resources with its host.

For more information, see the [Macrobenchmark documentation](https://d.android.com/macrobenchmark#create-macrobenchmark) and the [instrumentation arguments documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args).

## Constructors

| | |
|---|---|
| [StartupBenchmarks](-startup-benchmarks.md) | [main]<br>constructor() |

## Properties

| Name | Summary |
|---|---|
| [rule](rule.md) | [main]<br>val [rule](rule.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> |

## Functions

| Name | Summary |
|---|---|
| [startupCompilationBaselineProfiles](startup-compilation-baseline-profiles.md) | [main]<br>fun [startupCompilationBaselineProfiles](startup-compilation-baseline-profiles.md)() |
| [startupCompilationNone](startup-compilation-none.md) | [main]<br>fun [startupCompilationNone](startup-compilation-none.md)() |
