//[data](../../../index.md)/[core.data.room.dao](../index.md)/[HeaderSettingsDao](index.md)

# HeaderSettingsDao

[main]\
abstract class [HeaderSettingsDao](index.md)

## Constructors

| | |
|---|---|
| [HeaderSettingsDao](-header-settings-dao.md) | [main]<br>constructor() |

## Functions

| Name | Summary |
|---|---|
| [getRow](get-row.md) | [main]<br>abstract suspend fun [getRow](get-row.md)(): [HeaderSettings](../../core.data.prefs/-header-settings/index.md)? |
| [observe](observe.md) | [main]<br>open fun [observe](observe.md)(): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[HeaderSettings](../../core.data.prefs/-header-settings/index.md)&gt; |
| [observeRow](observe-row.md) | [main]<br>abstract fun [observeRow](observe-row.md)(): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[HeaderSettings](../../core.data.prefs/-header-settings/index.md)?&gt; |
| [update](update.md) | [main]<br>open suspend fun [update](update.md)(transform: ([HeaderSettings](../../core.data.prefs/-header-settings/index.md)) -&gt; [HeaderSettings](../../core.data.prefs/-header-settings/index.md)) |
| [updateIfChanged](update-if-changed.md) | [main]<br>open suspend fun [updateIfChanged](update-if-changed.md)(transform: ([HeaderSettings](../../core.data.prefs/-header-settings/index.md)) -&gt; [HeaderSettings](../../core.data.prefs/-header-settings/index.md)) |
| [upsert](upsert.md) | [main]<br>abstract suspend fun [upsert](upsert.md)(settings: [HeaderSettings](../../core.data.prefs/-header-settings/index.md)) |
