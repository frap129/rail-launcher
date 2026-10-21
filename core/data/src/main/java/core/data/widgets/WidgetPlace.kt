package core.data.widgets

/** An ordered slot in Home's Widget Area. A place holds one Widget or a Widget Stack. */
data class WidgetPlace(val id: Long, val heightDp: Int?, val currentPage: Int, val widgets: List<PlacedWidget>)

/** A Widget stored in a [WidgetPlace]. */
sealed interface PlacedWidget {
    val id: Long

    /** The built-in clock and date Widget. */
    data class Header(override val id: Long) : PlacedWidget

    /** A Widget hosted from another app. */
    data class AppWidget(
        override val id: Long,
        val appWidgetId: Int,
        val provider: WidgetProvider,
        val profileSerial: Long,
        val extendsUnderRail: Boolean
    ) : PlacedWidget
}

/** The provider of an App Widget, stored as its package name and class name. */
data class WidgetProvider(val packageName: String, val className: String) {
    /** The provider stored as "package/class". */
    val key: String
        get() = "$packageName/$className"

    companion object {
        /** Reads a provider stored as "package/class", or returns null when [key] is malformed. */
        fun fromKey(key: String): WidgetProvider? {
            val separator: Int = key.indexOf('/')
            if (separator <= 0 || separator == key.lastIndex) {
                return null
            }
            return WidgetProvider(
                packageName = key.substring(0, separator),
                className = key.substring(separator + 1)
            )
        }
    }
}
