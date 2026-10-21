package core.data.prefs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

private const val MIN_EVENT_COUNT: Int = 1
private const val MAX_EVENT_COUNT: Int = 3

@Entity(tableName = "event_settings")
data class EventSettings(
    @PrimaryKey val id: Int = 0,
    val enabled: Boolean = false,
    @ColumnInfo(name = "lookahead_hours") val lookaheadHours: Int = DEFAULT_LOOKAHEAD_HOURS,
    @ColumnInfo(name = "max_events") val maxEvents: Int = MIN_EVENT_COUNT,
    @ColumnInfo(name = "include_all_day") val includeAllDay: Boolean = true,
    @ColumnInfo(name = "excluded_calendar_ids") val excludedCalendarIds: Set<String> = emptySet()
) {
    val resolvedMaxEvents: Int get() = maxEvents.coerceIn(MIN_EVENT_COUNT, MAX_EVENT_COUNT)

    fun withValidMaxEvents(): EventSettings = copy(maxEvents = resolvedMaxEvents)

    companion object {
        const val DEFAULT_LOOKAHEAD_HOURS: Int = 12

        val LOOKAHEAD_OPTIONS: List<Int> = listOf(1, 3, 6, 12, 24, 48)
        val EVENT_COUNT_OPTIONS: List<Int> = (MIN_EVENT_COUNT..MAX_EVENT_COUNT).toList()
    }
}
