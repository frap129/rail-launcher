//[data](../../../index.md)/[core.data.widgets](../index.md)/[IWidgetLifecycleReconciler](index.md)/[reconcileAtStartup](reconcile-at-startup.md)

# reconcileAtStartup

[main]\
abstract suspend fun [reconcileAtStartup](reconcile-at-startup.md)(pendingIds: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[AppWidgetId](../-app-widget-id/index.md)&gt;)

Frees system ids Rail has no row for and drops rows whose system id is gone. Ids in [pendingIds](reconcile-at-startup.md) belong to an add flow that is still completing and are kept.
