package core.ui.model.data

/** The LazyColumn key of the flexible spacer that keeps Favorites at the bottom of Home. */
const val FLEXIBLE_SPACER_KEY: String = "flexibleSpacer"

/** The LazyColumn key of the empty Favorites hint. */
const val FAVORITES_EMPTY_HINT_KEY: String = "favoritesEmptyHint"

private const val PLACE_KEY_PREFIX: String = "place:"
private const val FAVORITE_KEY_PREFIX: String = "favorite:"

/** The LazyColumn key of a Widget Area place. */
fun placeKey(id: Long): String = "$PLACE_KEY_PREFIX$id"

/** The LazyColumn key of a Favorite on Home. */
fun favoriteKey(key: String): String = "$FAVORITE_KEY_PREFIX$key"

/** Whether [key] belongs to a Widget Area place rather than a Favorite or a spacer. */
fun isPlaceKey(key: Any?): Boolean = key is String && key.startsWith(PLACE_KEY_PREFIX)

/** Whether [key] belongs to a Favorite rather than a Widget Area place or a spacer. */
fun isFavoriteKey(key: Any?): Boolean = key is String && key.startsWith(FAVORITE_KEY_PREFIX)
