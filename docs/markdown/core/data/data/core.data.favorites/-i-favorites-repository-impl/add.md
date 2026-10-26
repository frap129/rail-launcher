//[data](../../../index.md)/[core.data.favorites](../index.md)/[IFavoritesRepositoryImpl](index.md)/[add](add.md)

# add

[main]\
open suspend override fun [add](add.md)(launchable: [Launchable](../../core.data.launchables.model/-launchable/index.md))

Appends [launchable](add.md) to the end of the Favorites. Does nothing if it already is one. Waiting for the change to become visible to [isFavorite](is-favorite.md) is best-effort and time-bounded.
