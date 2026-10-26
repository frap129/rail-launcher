//[data](../../../index.md)/[core.data.room.dao](../index.md)/[EventSettingsDao](index.md)

# EventSettingsDao

[main]\
abstract class [EventSettingsDao](index.md)

## Constructors

| | |
|---|---|
| [EventSettingsDao](-event-settings-dao.md) | [main]<br>constructor() |

## Functions

| Name | Summary |
|---|---|
| [getRow](get-row.md) | [main]<br>abstract suspend fun [getRow](get-row.md)(): [EventSettings](../../core.data.prefs/-event-settings/index.md)? |
| [observe](observe.md) | [main]<br>fun [observe](observe.md)(): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[EventSettings](../../core.data.prefs/-event-settings/index.md)&gt; |
| [observeRow](observe-row.md) | [main]<br>abstract fun [observeRow](observe-row.md)(): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[EventSettings](../../core.data.prefs/-event-settings/index.md)?&gt; |
| [update](update.md) | [main]<br>open suspend fun [update](update.md)(transform: ([EventSettings](../../core.data.prefs/-event-settings/index.md)) -&gt; [EventSettings](../../core.data.prefs/-event-settings/index.md)) |
| [upsert](upsert.md) | [main]<br>abstract suspend fun [upsert](upsert.md)(settings: [EventSettings](../../core.data.prefs/-event-settings/index.md)) |
