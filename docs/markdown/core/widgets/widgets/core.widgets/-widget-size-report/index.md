//[widgets](../../../index.md)/[core.widgets](../index.md)/[WidgetSizeReport](index.md)

# WidgetSizeReport

[main]\
data class [WidgetSizeReport](index.md)(val appWidgetIds: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;<!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&gt;, val widthDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val heightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html))

The page size to report to the App Widget providers after a Resize.

## Constructors

| | |
|---|---|
| [WidgetSizeReport](-widget-size-report.md) | [main]<br>constructor(appWidgetIds: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;<!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&gt;, widthDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), heightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)) |

## Properties

| Name | Summary |
|---|---|
| [appWidgetIds](app-widget-ids.md) | [main]<br>val [appWidgetIds](app-widget-ids.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;<!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&gt; |
| [heightDp](height-dp.md) | [main]<br>val [heightDp](height-dp.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |
| [widthDp](width-dp.md) | [main]<br>val [widthDp](width-dp.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |

## Functions

| Name | Summary |
|---|---|
| [toWidgetEvent](../to-widget-event.md) | [main]<br>fun [WidgetSizeReport](index.md).[toWidgetEvent](../to-widget-event.md)(): [WidgetEvent.ReportWidgetSize](../-widget-event/-report-widget-size/index.md)<br>The size report in the shared Widget event shape. |
