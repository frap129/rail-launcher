//[data](../../../index.md)/[core.data.popups](../index.md)/[PopupItem](index.md)

# PopupItem

sealed interface [PopupItem](index.md)

One Launchable or Shortcut shown in a Pop-up.

#### Inheritors

| |
|---|
| [Launchable](-launchable/index.md) |
| [Shortcut](-shortcut/index.md) |

## Types

| Name | Summary |
|---|---|
| [Launchable](-launchable/index.md) | [main]<br>data class [Launchable](-launchable/index.md)(val launchable: [Launchable](../../core.data.launchables.model/-launchable/index.md)) : [PopupItem](index.md)<br>An App or Rail Launchable the user added to the Pop-up. |
| [Shortcut](-shortcut/index.md) | [main]<br>data class [Shortcut](-shortcut/index.md)(val launchable: [ShortcutLaunchable](../../core.data.shortcuts/-shortcut-launchable/index.md), val isEnabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) : [PopupItem](index.md)<br>A Shortcut the user added to the Pop-up. |

## Properties

| Name | Summary |
|---|---|
| [launchable](launchable.md) | [main]<br>abstract val [launchable](launchable.md): [Launchable](../../core.data.launchables.model/-launchable/index.md) |
