//[data](../../../index.md)/[core.data.room.dao](../index.md)/[FavoriteDao](index.md)

# FavoriteDao

[main]\
interface [FavoriteDao](index.md)

## Functions

| Name | Summary |
|---|---|
| [add](add.md) | [main]<br>abstract suspend fun [add](add.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)) |
| [observeAll](observe-all.md) | [main]<br>abstract fun [observeAll](observe-all.md)(): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[FavoriteRecord](../../core.data.favorites/-favorite-record/index.md)&gt;&gt; |
| [remove](remove.md) | [main]<br>abstract suspend fun [remove](remove.md)(keys: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt;) |
| [update](update.md) | [main]<br>abstract suspend fun [update](update.md)(records: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[FavoriteRecord](../../core.data.favorites/-favorite-record/index.md)&gt;)<br>Rewrites the positions of [records](update.md) in one transaction. Records no longer stored are ignored. |
