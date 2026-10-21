package core.data.widgets

import kotlinx.coroutines.flow.Flow

interface IWidgetAreaRepository {
    /** The Widget Area places in display order. Places without Widgets are dropped. */
    val places: Flow<List<WidgetPlace>>

    /** Removes the Header and its place when the place becomes empty, returning the old place position. */
    suspend fun removeHeader(): Int?

    /** Restores the Header at [position], or does nothing when a Header already exists. */
    suspend fun restoreHeader(position: Int = 0)
}
