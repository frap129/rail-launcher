package core.data.widgets

import kotlinx.coroutines.flow.Flow

/** Where a newly added App Widget place goes in the Widget Area. */
sealed interface InsertPosition {
    /** Directly below the place with [placeId], shifting later places down. */
    data class Below(val placeId: Long) : InsertPosition

    /** At the end of the Widget Area. */
    data object End : InsertPosition
}

/** Everything needed to place a bound App Widget in the Widget Area. */
data class AddAppWidget(
    val appWidgetId: AppWidgetId,
    val provider: WidgetProvider,
    val profileSerial: Long,
    val insertAt: InsertPosition,
    val defaultHeightDp: Int
)

interface IWidgetAreaRepository {
    /** The Widget Area places in display order. Places without Widgets are dropped. */
    val places: Flow<List<WidgetPlace>>

    /** Removes the Header and its place when the place becomes empty, returning the old place position. */
    suspend fun removeHeader(): Int?

    /** Restores the Header at [position], or does nothing when a Header already exists. */
    suspend fun restoreHeader(position: Int = 0)

    /** Inserts a place holding [request] and returns the new Widget's row id. */
    suspend fun addAppWidget(request: AddAppWidget): Long

    /** Removes the Widget and its place when empty, returning its system id. */
    suspend fun removeWidget(widgetId: Long): AppWidgetId?
}
