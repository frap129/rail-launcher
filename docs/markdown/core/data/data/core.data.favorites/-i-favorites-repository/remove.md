//[data](../../../index.md)/[core.data.favorites](../index.md)/[IFavoritesRepository](index.md)/[remove](remove.md)

# remove

[main]\
abstract suspend fun [remove](remove.md)(launchable: [Launchable](../../core.data.launchables.model/-launchable/index.md))

Removes [launchable](remove.md) from the Favorites. Does nothing if it is not one. Waiting for the change to become visible to [isFavorite](is-favorite.md) is best-effort and time-bounded.
