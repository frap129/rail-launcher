//[widgets](../../../index.md)/[core.widgets](../index.md)/[IWidgetResizeController](index.md)

# IWidgetResizeController

interface [IWidgetResizeController](index.md)

Owns Resize: provider bounds, the draft height, persistence, and one size report per session.

#### Inheritors

| |
|---|
| [IWidgetResizeControllerImpl](../-i-widget-resize-controller-impl/index.md) |

## Properties

| Name | Summary |
|---|---|
| [homeHeightDp](home-height-dp.md) | [main]<br>abstract val [homeHeightDp](home-height-dp.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?&gt; |
| [resizeState](resize-state.md) | [main]<br>abstract val [resizeState](resize-state.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[ResizeState](../-resize-state/index.md)?&gt; |

## Functions

| Name | Summary |
|---|---|
| [cancelResize](cancel-resize.md) | [main]<br>abstract fun [cancelResize](cancel-resize.md)()<br>Leaves Resize without saving when the place disappears underneath the user. |
| [clampStackHeight](clamp-stack-height.md) | [main]<br>abstract suspend fun [clampStackHeight](clamp-stack-height.md)(placeId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), headerHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?, ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null)<br>Clamps a stack's stored height after the Header is replaced by a page. |
| [dragResize](drag-resize.md) | [main]<br>abstract fun [dragResize](drag-resize.md)(deltaDp: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html))<br>Applies a fractional drag delta, snapping the accumulated height to the 8dp grid. |
| [finishResize](finish-resize.md) | [main]<br>abstract fun [finishResize](finish-resize.md)(): [ResizeFinish](../-resize-finish/index.md)?<br>Leaves Resize and returns the height persistence and size report it still needs. |
| [finishResizeAndReport](../finish-resize-and-report.md) | [main]<br>fun [IWidgetResizeController](index.md).[finishResizeAndReport](../finish-resize-and-report.md)(scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, onReport: suspend ([WidgetSizeReport](../-widget-size-report/index.md)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Leaves Resize, stores its height and reports the new size; false when no Resize was active. |
| [onPlacesChanged](on-places-changed.md) | [main]<br>abstract fun [onPlacesChanged](on-places-changed.md)(places: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;<!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&gt;, ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null)<br>Cancels Resize when its place disappears. |
| [onStackGrown](on-stack-grown.md) | [main]<br>abstract suspend fun [onStackGrown](on-stack-grown.md)(placeId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), headerHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?, ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null): [WidgetSizeReport](../-widget-size-report/index.md)?<br>Clamps a stack that gained a page and returns the page size to report to its providers. |
| [persistResize](persist-resize.md) | [main]<br>abstract suspend fun [persistResize](persist-resize.md)(finish: [ResizeFinish](../-resize-finish/index.md)): [WidgetSizeReport](../-widget-size-report/index.md)?<br>Persists [finish](persist-resize.md) and returns the size report to send, if any. |
| [resetResize](reset-resize.md) | [main]<br>abstract fun [resetResize](reset-resize.md)()<br>Returns the draft to the provider default, which [finishResize](finish-resize.md) then stores as no override. |
| [setHomeHeight](set-home-height.md) | [main]<br>abstract fun [setHomeHeight](set-home-height.md)(heightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html))<br>Supplies the safe Home height so Resize can cap the widget and keep the handle reachable. |
| [setWidgetAreaWidth](set-widget-area-width.md) | [main]<br>abstract fun [setWidgetAreaWidth](set-widget-area-width.md)(widthDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html))<br>Supplies the Widget Area width for the size report sent to the provider after Resize. |
| [startResize](start-resize.md) | [main]<br>abstract suspend fun [startResize](start-resize.md)(placeId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), headerHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)? = null, ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Enters Resize for [placeId](start-resize.md), returning false when the place is not resizable right now. |
| [startResizeForWidget](start-resize-for-widget.md) | [main]<br>abstract suspend fun [startResizeForWidget](start-resize-for-widget.md)(widgetId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), headerHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)? = null, ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Enters Resize for the place holding [widgetId](start-resize-for-widget.md). |
