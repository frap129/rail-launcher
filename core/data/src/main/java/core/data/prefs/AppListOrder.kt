package core.data.prefs

/** How the App List orders the Launchables inside each group. */
enum class AppListOrder {
    ALPHABETICAL,
    MOST_USED,
    RECENTLY_USED;

    companion object {
        val DEFAULT: AppListOrder = RECENTLY_USED
    }
}
