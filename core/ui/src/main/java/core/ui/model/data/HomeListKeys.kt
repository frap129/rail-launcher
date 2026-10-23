package core.ui.model.data

/** The LazyColumn key of the empty Favorites hint. */
const val FAVORITES_EMPTY_HINT_KEY: String = "favoritesEmptyHint"

private const val PLACE_KEY_PREFIX: String = "place:"
private const val FAVORITE_KEY_PREFIX: String = "favorite:"

/** The LazyColumn key of a Widget Area place. */
fun placeKey(id: Long): String = "$PLACE_KEY_PREFIX$id"

/** The LazyColumn key of a Favorite on Home. */
fun favoriteKey(key: String): String = "$FAVORITE_KEY_PREFIX$key"

/** Whether [key] belongs to a Widget Area place rather than another Home item. */
fun isPlaceKey(key: Any?): Boolean = key is String && key.startsWith(PLACE_KEY_PREFIX)

/** Whether [key] belongs to a Favorite rather than another Home item. */
fun isFavoriteKey(key: Any?): Boolean = key is String && key.startsWith(FAVORITE_KEY_PREFIX)
