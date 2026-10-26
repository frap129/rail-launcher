//[data](../../../index.md)/[core.data.widgets](../index.md)/[IWidgetLifecycleReconcilerImpl](index.md)

# IWidgetLifecycleReconcilerImpl

[main]\
class [IWidgetLifecycleReconcilerImpl](index.md)(dao: [WidgetAreaDao](../../core.data.room.dao/-widget-area-dao/index.md), host: [IAppWidgetHostSource](../-i-app-widget-host-source/index.md)) : [IWidgetLifecycleReconciler](../-i-widget-lifecycle-reconciler/index.md)

Keeps Rail's App Widget database in step with the ids and providers the system knows.

## Constructors

| | |
|---|---|
| [IWidgetLifecycleReconcilerImpl](-i-widget-lifecycle-reconciler-impl.md) | [main]<br>constructor(dao: [WidgetAreaDao](../../core.data.room.dao/-widget-area-dao/index.md), host: [IAppWidgetHostSource](../-i-app-widget-host-source/index.md)) |

## Properties

| Name | Summary |
|---|---|
| [isStartupReconciled](is-startup-reconciled.md) | [main]<br>open override val [isStartupReconciled](is-startup-reconciled.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)&gt;<br>True once [reconcileAtStartup](reconcile-at-startup.md) has completed, so App Widgets can be rendered. |
| [removedIds](removed-ids.md) | [main]<br>open override val [removedIds](removed-ids.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[AppWidgetId](../-app-widget-id/index.md)&gt;<br>App Widget ids whose database row was removed by reconciliation. |

## Functions

| Name | Summary |
|---|---|
| [reconcileAtStartup](reconcile-at-startup.md) | [main]<br>open suspend override fun [reconcileAtStartup](reconcile-at-startup.md)(pendingIds: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[AppWidgetId](../-app-widget-id/index.md)&gt;)<br>Frees system ids Rail has no row for and drops rows whose system id is gone. Ids in [pendingIds](reconcile-at-startup.md) belong to an add flow that is still completing and are kept. |
| [removeGoneProviders](remove-gone-providers.md) | [main]<br>open suspend override fun [removeGoneProviders](remove-gone-providers.md)()<br>Removes rows whose provider package is no longer installed for its profile. |
