//[data](../../../index.md)/[core.data.widgets](../index.md)/[WidgetAreaSettingsRecord](index.md)

# WidgetAreaSettingsRecord

[main]\
data class [WidgetAreaSettingsRecord](index.md)(val id: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0, val maxStackPages: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = WidgetAreaSettings.DEFAULT_MAX_STACK_PAGES, val stackIndicatorVisible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, val heightLimitEnabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, val heightLimit: [WidgetAreaHeightLimit](../-widget-area-height-limit/index.md) = WidgetAreaHeightLimit.DEFAULT, val topOffsetDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0, val widgetSpacing: [WidgetSpacing](../-widget-spacing/index.md) = WidgetSpacing.DEFAULT)

Persisted preferences for the Widget Area and its stacks.

## Constructors

| | |
|---|---|
| [WidgetAreaSettingsRecord](-widget-area-settings-record.md) | [main]<br>constructor(id: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0, maxStackPages: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = WidgetAreaSettings.DEFAULT_MAX_STACK_PAGES, stackIndicatorVisible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, heightLimitEnabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, heightLimit: [WidgetAreaHeightLimit](../-widget-area-height-limit/index.md) = WidgetAreaHeightLimit.DEFAULT, topOffsetDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0, widgetSpacing: [WidgetSpacing](../-widget-spacing/index.md) = WidgetSpacing.DEFAULT) |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [main]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [heightLimit](height-limit.md) | [main]<br>val [heightLimit](height-limit.md): [WidgetAreaHeightLimit](../-widget-area-height-limit/index.md) |
| [heightLimitEnabled](height-limit-enabled.md) | [main]<br>val [heightLimitEnabled](height-limit-enabled.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true |
| [id](id.md) | [main]<br>val [id](id.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0 |
| [maxStackPages](max-stack-pages.md) | [main]<br>val [maxStackPages](max-stack-pages.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |
| [stackIndicatorVisible](stack-indicator-visible.md) | [main]<br>val [stackIndicatorVisible](stack-indicator-visible.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true |
| [topOffsetDp](top-offset-dp.md) | [main]<br>val [topOffsetDp](top-offset-dp.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0 |
| [widgetSpacing](widget-spacing.md) | [main]<br>val [widgetSpacing](widget-spacing.md): [WidgetSpacing](../-widget-spacing/index.md) |

## Functions

| Name | Summary |
|---|---|
| [toDomain](to-domain.md) | [main]<br>fun [toDomain](to-domain.md)(): [WidgetAreaSettings](../-widget-area-settings/index.md)<br>Reads the row as a valid domain value, coercing a corrupt range back into bounds. |
