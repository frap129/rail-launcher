//[popups](../../../index.md)/[core.popups](../index.md)/[SwipeToOpen](index.md)

# SwipeToOpen

[main]\
object [SwipeToOpen](index.md)

The pure thresholds and offsets behind the Pop-up row swipe.

## Properties

| Name | Summary |
|---|---|
| [MAX_OFFSET_DP](-m-a-x_-o-f-f-s-e-t_-d-p.md) | [main]<br>const val [MAX_OFFSET_DP](-m-a-x_-o-f-f-s-e-t_-d-p.md): [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html) = 72.0f |
| [OPEN_THRESHOLD_DP](-o-p-e-n_-t-h-r-e-s-h-o-l-d_-d-p.md) | [main]<br>const val [OPEN_THRESHOLD_DP](-o-p-e-n_-t-h-r-e-s-h-o-l-d_-d-p.md): [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html) = 48.0f |

## Functions

| Name | Summary |
|---|---|
| [offsetFor](offset-for.md) | [main]<br>fun [offsetFor](offset-for.md)(dxPx: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html), density: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)): [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)<br>The offset the row content shows while [dxPx](offset-for.md) toward the Rail has been dragged. |
| [onDrag](on-drag.md) | [main]<br>fun [onDrag](on-drag.md)(dx: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html), dy: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html), isRtl: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), slopPx: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)): [SwipeClaim](../-swipe-claim/index.md)<br>Classifies a cumulative drag. Only a horizontal-dominant drag toward the Rail side is claimable. |
| [onRelease](on-release.md) | [main]<br>fun [onRelease](on-release.md)(dx: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html), velocityX: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html), isRtl: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), thresholdPx: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html), velocityThresholdPx: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Whether releasing a claimed drag opens the Pop-up. |
| [thresholdPx](threshold-px.md) | [main]<br>fun [thresholdPx](threshold-px.md)(density: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)): [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)<br>The release distance threshold in pixels. |
| [velocityThresholdPx](velocity-threshold-px.md) | [main]<br>fun [velocityThresholdPx](velocity-threshold-px.md)(density: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)): [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)<br>The release velocity threshold in pixels per second. |
