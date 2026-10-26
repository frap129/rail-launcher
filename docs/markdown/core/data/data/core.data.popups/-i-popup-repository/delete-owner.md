//[data](../../../index.md)/[core.data.popups](../index.md)/[IPopupRepository](index.md)/[deleteOwner](delete-owner.md)

# deleteOwner

[main]\
abstract suspend fun [deleteOwner](delete-owner.md)(ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[AppWidgetId](../../core.data.widgets/-app-widget-id/index.md)&gt;

Deletes the stored Pop-up preferences of [ownerKey](delete-owner.md), which no longer exists, and frees its Widgets.
