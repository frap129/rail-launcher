//[data](../../../index.md)/[core.data.favorites](../index.md)/[IFavoritesRepositoryImpl](index.md)/[remove](remove.md)

# remove

[main]\
open suspend override fun [remove](remove.md)(launchable: [Launchable](../../core.data.launchables.model/-launchable/index.md))

Removes [launchable](remove.md) from the Favorites. Does nothing if it is not one. Waiting for the change to become visible to [isFavorite](is-favorite.md) is best-effort and time-bounded.
