//[widgets](../../../index.md)/[core.widgets](../index.md)/[RailAppWidgetHostView](index.md)

# RailAppWidgetHostView

[main]\
class [RailAppWidgetHostView](index.md)(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->)

The host view Rail shows for every App Widget. It intercepts a long press before a child can turn it into a click and disables the system's default widget padding, since the Widget Area already has edge insets.

## Constructors

| | |
|---|---|
| [RailAppWidgetHostView](-rail-app-widget-host-view.md) | [main]<br>constructor(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |

## Properties

| Name | Summary |
|---|---|
| [onLongPress](on-long-press.md) | [main]<br>var [onLongPress](on-long-press.md): () -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html)? |

## Functions

| Name | Summary |
|---|---|
| [onInterceptTouchEvent](on-intercept-touch-event.md) | [main]<br>open fun [onInterceptTouchEvent](on-intercept-touch-event.md)(event: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) |
| [requestDisallowInterceptTouchEvent](request-disallow-intercept-touch-event.md) | [main]<br>open fun [requestDisallowInterceptTouchEvent](request-disallow-intercept-touch-event.md)(disallowIntercept: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setAppWidget](set-app-widget.md) | [main]<br>open fun [setAppWidget](set-app-widget.md)(appWidgetId: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), info: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->?) |
