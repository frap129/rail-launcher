//[widgets](../../../index.md)/[core.widgets](../index.md)/[IWidgetResizeController](index.md)/[onStackGrown](on-stack-grown.md)

# onStackGrown

[main]\
abstract suspend fun [onStackGrown](on-stack-grown.md)(placeId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), headerHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?, ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null): [WidgetSizeReport](../-widget-size-report/index.md)?

Clamps a stack that gained a page and returns the page size to report to its providers.
