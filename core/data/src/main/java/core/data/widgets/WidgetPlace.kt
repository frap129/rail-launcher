package core.data.widgets

/** An ordered slot in Home's Widget Area. A place holds one Widget or a Widget Stack. */
data class WidgetPlace(val id: Long, val heightDp: Int?, val currentPage: Int, val widgets: List<PlacedWidget>) {
    /** True when this place pages sideways as a Widget Stack. */
    val isStack: Boolean get() = widgets.size > 1

    /** How wide this place is laid out; only a lone App Widget can extend under the Rail. */
    val widthMode: WidgetWidth
        get() {
            val widget: PlacedWidget.AppWidget = widgets.singleOrNull() as? PlacedWidget.AppWidget ?: return WidgetWidth.Inset
            return if (widget.extendsUnderRail) WidgetWidth.UnderRail else WidgetWidth.Inset
        }
}

/** The place holding the Widget with [widgetId], if any. */
fun List<WidgetPlace>.placeOf(widgetId: Long): WidgetPlace? = firstOrNull { place ->
    place.widgets.any { widget -> widget.id == widgetId }
}

/** How wide a Widget is laid out relative to the Rail's touch column. */
enum class WidgetWidth {
    Inset,
    UnderRail
}

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
        val extendsUnderRail: Boolean,
        val availability: WidgetAvailability = WidgetAvailability.Available,
        val defaultHeightDp: Int? = null
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
