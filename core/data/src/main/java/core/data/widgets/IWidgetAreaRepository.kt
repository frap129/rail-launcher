package core.data.widgets

import kotlinx.coroutines.flow.Flow

/** Where a newly added App Widget place goes in the Widget Area. */
sealed interface InsertPosition {
    /** Directly below the place with [placeId], shifting later places down. */
    data class Below(val placeId: Long) : InsertPosition

    /** At the end of the Widget Area. */
    data object End : InsertPosition

    /** Appended as a new page of the Widget Stack in [placeId]. */
    data class IntoStack(val placeId: Long) : InsertPosition
}

/** Everything needed to place a bound App Widget in the Widget Area. */
data class AddAppWidget(
    val appWidgetId: AppWidgetId,
    val provider: WidgetProvider,
    val profileSerial: Long,
    val insertAt: InsertPosition,
    val defaultHeightDp: Int
)

/** One place in an Edit Widgets draft; a null [placeId] is a place created by splitting a stack. */
data class PlaceDraft(val placeId: Long?, val widgetIds: List<Long>)

/** The complete desired Widget Area layout committed atomically on Done. */
data class WidgetAreaEdit(val places: List<PlaceDraft>, val removedWidgetIds: Set<Long> = emptySet())

/** Where a removed Header used to live, for Undo. */
data class HeaderRemoval(val position: Int, val placeId: Long, val pagePosition: Int)

/** Everything Undo needs to restore a Header replaced by an App Widget. */
data class ReplaceHeaderUndo(
    val widgetId: Long,
    val headerPlaceId: Long,
    val headerPosition: Int,
    val headerPagePosition: Int,
    val headerHeightDp: Int?,
    val headerCurrentPage: Int,
    val widgetPlaceId: Long,
    val widgetPosition: Int,
    val widgetPagePosition: Int,
    val widgetHeightDp: Int?,
    val widgetCurrentPage: Int
)

interface IWidgetAreaRepository {
    /** The Widget Area places in display order. Places without Widgets are dropped. */
    val places: Flow<List<WidgetPlace>>

    /** Moves [widgetId] into the Header's place and removes the Header, returning how to undo it. */
    suspend fun replaceHeader(widgetId: Long): ReplaceHeaderUndo?

    /** Restores the Header and [ReplaceHeaderUndo.widgetId] to their places from before Replace Header. */
    suspend fun undoReplaceHeader(undo: ReplaceHeaderUndo)

    /** Stores whether a lone App Widget extends under the Rail. */
    suspend fun setExtendsUnderRail(widgetId: Long, extendsUnderRail: Boolean)

    /** Removes the Header, returning where it used to live. */
    suspend fun removeHeader(): HeaderRemoval?

    /** Removes only [widgetId]'s Header page from its stack, returning where it used to live. */
    suspend fun removeHeaderPage(widgetId: Long): HeaderRemoval?

    /** Restores the Header at [position] or into [placeId] at [pagePosition] when that place still exists. */
    suspend fun restoreHeader(position: Int = 0, placeId: Long? = null, pagePosition: Int = 0)

    /** Inserts a place holding [request] (or appends a page for [InsertPosition.IntoStack]) and returns the Widget's row id. */
    suspend fun addAppWidget(request: AddAppWidget): Long

    /** Removes the Widget, dissolving a stack to one page and deleting an empty place; returns its system id. */
    suspend fun removeWidget(widgetId: Long): AppWidgetId?

    /** Stores the place height, or null to fall back to the provider default. */
    suspend fun setPlaceHeight(placeId: Long, heightDp: Int?)

    /** Stores the stack's current page. */
    suspend fun setCurrentPage(placeId: Long, page: Int)

    /** Applies one Edit Widgets draft atomically and returns the App Widget ids freed by removals. */
    suspend fun commitEdit(edit: WidgetAreaEdit): List<AppWidgetId>
}
