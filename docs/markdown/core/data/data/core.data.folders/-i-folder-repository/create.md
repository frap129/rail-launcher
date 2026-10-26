//[data](../../../index.md)/[core.data.folders](../index.md)/[IFolderRepository](index.md)/[create](create.md)

# create

[main]\
abstract suspend fun [create](create.md)(name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)?, initialItems: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt; = emptyList(), addToFavorites: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true): [Folder](../-folder/index.md)

Creates a Folder, stores [name](create.md) as its customization when it is not blank or the default, adds [initialItems](create.md) to its Pop-up and, when [addToFavorites](create.md) is true, appends it to the Favorites.
