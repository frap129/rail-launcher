//[data](../../../index.md)/[core.data.room.dao](../index.md)/[WidgetAreaSettingsDao](index.md)

# WidgetAreaSettingsDao

[main]\
abstract class [WidgetAreaSettingsDao](index.md)

## Constructors

| | |
|---|---|
| [WidgetAreaSettingsDao](-widget-area-settings-dao.md) | [main]<br>constructor() |

## Functions

| Name | Summary |
|---|---|
| [getRow](get-row.md) | [main]<br>abstract suspend fun [getRow](get-row.md)(): [WidgetAreaSettingsRecord](../../core.data.widgets/-widget-area-settings-record/index.md)? |
| [observe](observe.md) | [main]<br>fun [observe](observe.md)(): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[WidgetAreaSettings](../../core.data.widgets/-widget-area-settings/index.md)&gt; |
| [observeRow](observe-row.md) | [main]<br>abstract fun [observeRow](observe-row.md)(): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[WidgetAreaSettingsRecord](../../core.data.widgets/-widget-area-settings-record/index.md)?&gt; |
| [update](update.md) | [main]<br>open suspend fun [update](update.md)(transform: ([WidgetAreaSettings](../../core.data.widgets/-widget-area-settings/index.md)) -&gt; [WidgetAreaSettings](../../core.data.widgets/-widget-area-settings/index.md)) |
| [upsert](upsert.md) | [main]<br>abstract suspend fun [upsert](upsert.md)(settings: [WidgetAreaSettingsRecord](../../core.data.widgets/-widget-area-settings-record/index.md)) |
