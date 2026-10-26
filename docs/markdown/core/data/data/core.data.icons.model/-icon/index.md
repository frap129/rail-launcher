//[data](../../../index.md)/[core.data.icons.model](../index.md)/[Icon](index.md)

# Icon

sealed class [Icon](index.md)

#### Inheritors

| |
|---|
| [RailIcon](-rail-icon/index.md) |
| [ActivityIcon](-activity-icon/index.md) |
| [ApplicationIcon](-application-icon/index.md) |
| [ShortcutIcon](-shortcut-icon/index.md) |
| [FolderIcon](-folder-icon/index.md) |
| [PackIcon](-pack-icon/index.md) |

## Types

| Name | Summary |
|---|---|
| [ActivityIcon](-activity-icon/index.md) | [main]<br>data class [ActivityIcon](-activity-icon/index.md)(val componentName: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [Icon](index.md) |
| [ApplicationIcon](-application-icon/index.md) | [main]<br>data class [ApplicationIcon](-application-icon/index.md)(val packageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)) : [Icon](index.md) |
| [FolderIcon](-folder-icon/index.md) | [main]<br>data class [FolderIcon](-folder-icon/index.md)(val shade: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)) : [Icon](index.md)<br>A Rail-drawn Folder icon in one of three accent shades. |
| [PackIcon](-pack-icon/index.md) | [main]<br>data class [PackIcon](-pack-icon/index.md)(val componentName: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, val packPackageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val resId: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)) : [Icon](index.md) |
| [RailIcon](-rail-icon/index.md) | [main]<br>data class [RailIcon](-rail-icon/index.md)(val resId: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)) : [Icon](index.md) |
| [ShortcutIcon](-shortcut-icon/index.md) | [main]<br>data class [ShortcutIcon](-shortcut-icon/index.md)(val packageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val shortcutId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val profile: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [Icon](index.md) |
