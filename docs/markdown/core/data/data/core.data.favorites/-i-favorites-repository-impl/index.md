//[data](../../../index.md)/[core.data.favorites](../index.md)/[IFavoritesRepositoryImpl](index.md)

# IFavoritesRepositoryImpl

[main]\
class [IFavoritesRepositoryImpl](index.md)(favoriteDao: [FavoriteDao](../../core.data.room.dao/-favorite-dao/index.md), launchableRepository: [ILaunchableRepository](../../core.data.launchables/-i-launchable-repository/index.md), scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [IFavoritesRepository](../-i-favorites-repository/index.md)

## Constructors

| | |
|---|---|
| [IFavoritesRepositoryImpl](-i-favorites-repository-impl.md) | [main]<br>constructor(favoriteDao: [FavoriteDao](../../core.data.room.dao/-favorite-dao/index.md), launchableRepository: [ILaunchableRepository](../../core.data.launchables/-i-launchable-repository/index.md), scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) |

## Properties

| Name | Summary |
|---|---|
| [favorites](favorites.md) | [main]<br>open override val [favorites](favorites.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../../core.data.launchables.model/-launchable/index.md)&gt;&gt;<br>The Favorites in the user's order, resolved to their Launchables. A Favorite whose Launchable no longer exists is removed by the repository and never emitted. Collecting is a pure read. |

## Functions

| Name | Summary |
|---|---|
| [add](add.md) | [main]<br>open suspend override fun [add](add.md)(launchable: [Launchable](../../core.data.launchables.model/-launchable/index.md))<br>Appends [launchable](add.md) to the end of the Favorites. Does nothing if it already is one. Waiting for the change to become visible to [isFavorite](is-favorite.md) is best-effort and time-bounded. |
| [isFavorite](is-favorite.md) | [main]<br>open override fun [isFavorite](is-favorite.md)(launchable: [Launchable](../../core.data.launchables.model/-launchable/index.md)): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)&gt;<br>Whether [launchable](is-favorite.md) is a Favorite. False while the Launchables have not loaded, and false for a stored record whose Launchable cannot currently be resolved. |
| [remove](remove.md) | [main]<br>open suspend override fun [remove](remove.md)(launchable: [Launchable](../../core.data.launchables.model/-launchable/index.md))<br>Removes [launchable](remove.md) from the Favorites. Does nothing if it is not one. Waiting for the change to become visible to [isFavorite](is-favorite.md) is best-effort and time-bounded. |
| [reorder](reorder.md) | [main]<br>open suspend override fun [reorder](reorder.md)(favorites: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../../core.data.launchables.model/-launchable/index.md)&gt;)<br>Stores [favorites](reorder.md) as the Favorites' order. Launchables that are not Favorites are ignored. |
