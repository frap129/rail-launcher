//[widgets](../../../index.md)/[core.widgets](../index.md)/[IAppWidgetViewCacheImpl](index.md)

# IAppWidgetViewCacheImpl

[main]\
class [IAppWidgetViewCacheImpl](index.md)(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, host: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [IAppWidgetViewCache](../-i-app-widget-view-cache/index.md)

One RailAppWidgetHostView per App Widget id, kept for the process lifetime.

## Constructors

| | |
|---|---|
| [IAppWidgetViewCacheImpl](-i-app-widget-view-cache-impl.md) | [main]<br>constructor(context: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, host: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |

## Functions

| Name | Summary |
|---|---|
| [getOrCreate](get-or-create.md) | [main]<br>open override fun [getOrCreate](get-or-create.md)(id: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->): [RailAppWidgetHostView](../-rail-app-widget-host-view/index.md)?<br>Returns the cached view, which may still be attached to its current holder. |
| [remove](remove.md) | [main]<br>open override fun [remove](remove.md)(id: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |
| [reportSizeIfChanged](report-size-if-changed.md) | [main]<br>open override fun [reportSizeIfChanged](report-size-if-changed.md)(id: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, widthDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), heightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html))<br>Reports [widthDp](report-size-if-changed.md) × [heightDp](report-size-if-changed.md) unless the same size was already sent for this widget. |
| [takeForAttach](take-for-attach.md) | [main]<br>open override fun [takeForAttach](take-for-attach.md)(id: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->): [RailAppWidgetHostView](../-rail-app-widget-host-view/index.md)?<br>Returns the cached view detached from its previous holder, ready to attach to a new one. |
| [updateSize](update-size.md) | [main]<br>open override fun [updateSize](update-size.md)(ids: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;<!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&gt;, widthDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), heightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html))<br>Reports the persisted Widget Area size to the providers of the resized place. |
