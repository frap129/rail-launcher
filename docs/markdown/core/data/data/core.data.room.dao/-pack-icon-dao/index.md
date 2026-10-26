//[data](../../../index.md)/[core.data.room.dao](../index.md)/[PackIconDao](index.md)

# PackIconDao

[main]\
interface [PackIconDao](index.md)

## Functions

| Name | Summary |
|---|---|
| [delete](delete.md) | [main]<br>abstract suspend fun [delete](delete.md)(icon: [Icon.PackIcon](../../core.data.icons.model/-icon/-pack-icon/index.md)) |
| [deleteAll](delete-all.md) | [main]<br>abstract suspend fun [deleteAll](delete-all.md)(icons: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Icon.PackIcon](../../core.data.icons.model/-icon/-pack-icon/index.md)&gt;) |
| [getAll](get-all.md) | [main]<br>abstract suspend fun [getAll](get-all.md)(): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Icon.PackIcon](../../core.data.icons.model/-icon/-pack-icon/index.md)&gt; |
| [getAllFromPack](get-all-from-pack.md) | [main]<br>abstract suspend fun [getAllFromPack](get-all-from-pack.md)(packPackageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Icon.PackIcon](../../core.data.icons.model/-icon/-pack-icon/index.md)&gt; |
| [getIcon](get-icon.md) | [main]<br>abstract suspend fun [getIcon](get-icon.md)(componentName: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, packPackageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): [Icon.PackIcon](../../core.data.icons.model/-icon/-pack-icon/index.md)? |
| [insert](insert.md) | [main]<br>abstract suspend fun [insert](insert.md)(icon: [Icon.PackIcon](../../core.data.icons.model/-icon/-pack-icon/index.md)) |
| [insertAll](insert-all.md) | [main]<br>abstract suspend fun [insertAll](insert-all.md)(icons: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Icon.PackIcon](../../core.data.icons.model/-icon/-pack-icon/index.md)&gt;) |
