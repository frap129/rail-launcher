//[data](../../index.md)/[core.data.popups](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [IPopupRepository](-i-popup-repository/index.md) | [main]<br>interface [IPopupRepository](-i-popup-repository/index.md)<br>Per-App Pop-up preferences. |
| [IPopupRepositoryImpl](-i-popup-repository-impl/index.md) | [main]<br>class [IPopupRepositoryImpl](-i-popup-repository-impl/index.md)(popupDao: [PopupDao](../core.data.room.dao/-popup-dao/index.md), launchableRepository: [ILaunchableRepository](../core.data.launchables/-i-launchable-repository/index.md), shortcutSource: [IShortcutSource](../core.data.shortcuts/-i-shortcut-source/index.md), scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, widgetAreaRepository: [IWidgetAreaRepository](../core.data.widgets/-i-widget-area-repository/index.md), removeOwnerWidgets: [IRemoveOwnerWidgets](../core.data.widgets/-i-remove-owner-widgets/index.md)) : [IPopupRepository](-i-popup-repository/index.md) |
| [PopupHiddenShortcut](-popup-hidden-shortcut/index.md) | [main]<br>data class [PopupHiddenShortcut](-popup-hidden-shortcut/index.md)(val ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val shortcutId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html))<br>A shortcut the user hid from one App's Pop-up. |
| [PopupItem](-popup-item/index.md) | [main]<br>sealed interface [PopupItem](-popup-item/index.md)<br>One Launchable or Shortcut shown in a Pop-up. |
| [PopupItemRecord](-popup-item-record/index.md) | [main]<br>data class [PopupItemRecord](-popup-item-record/index.md)(val ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val itemKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val position: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html))<br>A Launchable the user added to one App's Pop-up, at [position](-popup-item-record/position.md) in the user's order. |
| [PopupSettingsRecord](-popup-settings-record/index.md) | [main]<br>data class [PopupSettingsRecord](-popup-settings-record/index.md)(val id: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0, val notificationsEnabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false)<br>Rail's Pop-up preferences, held in a single row. |
