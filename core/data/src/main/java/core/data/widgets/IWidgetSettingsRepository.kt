package core.data.widgets

import core.data.prefs.BatteryVisibility
import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.prefs.withBatteryVisibility
import core.data.prefs.withClockVisible
import core.data.prefs.withDateVisible
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface IWidgetSettingsRepository {
    val headerSettings: Flow<HeaderSettings>

    val eventSettings: Flow<EventSettings>

    val widgetAreaSettings: Flow<WidgetAreaSettings>

    suspend fun updateHeaderSettings(transform: (HeaderSettings) -> HeaderSettings)

    suspend fun updateEventSettings(transform: (EventSettings) -> EventSettings)

    suspend fun updateWidgetAreaSettings(transform: (WidgetAreaSettings) -> WidgetAreaSettings)

    suspend fun setMaxStackPages(maxStackPages: Int) {
        updateWidgetAreaIfChanged { settings ->
            settings.copy(
                maxStackPages = maxStackPages.coerceIn(
                    WidgetAreaSettings.MIN_STACK_PAGES,
                    WidgetAreaSettings.MAX_STACK_PAGES
                )
            )
        }
    }

    suspend fun setStackIndicatorVisible(visible: Boolean) {
        updateWidgetAreaIfChanged { settings -> settings.copy(stackIndicatorVisible = visible) }
    }

    suspend fun setHeightLimitEnabled(enabled: Boolean) {
        updateWidgetAreaIfChanged { settings -> settings.copy(heightLimitEnabled = enabled) }
    }

    suspend fun setHeightLimit(limit: WidgetAreaHeightLimit) {
        updateWidgetAreaIfChanged { settings -> settings.copy(heightLimit = limit) }
    }

    suspend fun setTopOffsetDp(offsetDp: Int) {
        val snapped: Int = WidgetAreaSettings.snapOffsetToStep(offsetDp = offsetDp)
        updateWidgetAreaIfChanged { settings -> settings.copy(topOffsetDp = snapped) }
    }

    suspend fun setWidgetSpacing(spacing: WidgetSpacing) {
        updateWidgetAreaIfChanged { settings -> settings.copy(widgetSpacing = spacing) }
    }

    suspend fun setClockVisible(visible: Boolean) {
        updateIfChanged { settings -> settings.withClockVisible(visible) }
    }

    suspend fun setDateVisible(visible: Boolean) {
        updateIfChanged { settings -> settings.withDateVisible(visible) }
    }

    suspend fun setBatteryVisibility(visibility: BatteryVisibility) {
        updateIfChanged { settings -> settings.withBatteryVisibility(visibility) }
    }

    suspend fun setEventsEnabled(enabled: Boolean) {
        updateEventIfChanged { settings -> settings.copy(enabled = enabled) }
    }

    suspend fun setEventLookaheadHours(hours: Int) {
        updateEventIfChanged { settings -> settings.copy(lookaheadHours = hours) }
    }

    suspend fun setEventMaxEvents(maxEvents: Int) {
        updateEventIfChanged { settings -> settings.copy(maxEvents = maxEvents).withValidMaxEvents() }
    }

    suspend fun setEventIncludeAllDay(includeAllDay: Boolean) {
        updateEventIfChanged { settings -> settings.copy(includeAllDay = includeAllDay) }
    }

    suspend fun setExcludedCalendarIds(calendarIds: Set<String>) {
        updateEventIfChanged { settings -> settings.copy(excludedCalendarIds = calendarIds) }
    }

    suspend fun pruneExcludedCalendarIds(availableCalendarIds: Set<String>) {
        if (availableCalendarIds.isEmpty()) return
        updateEventIfChanged { settings ->
            settings.copy(excludedCalendarIds = settings.excludedCalendarIds.intersect(availableCalendarIds))
        }
    }
}

private suspend fun IWidgetSettingsRepository.updateIfChanged(transform: (HeaderSettings) -> HeaderSettings) {
    val current: HeaderSettings = headerSettings.first()
    if (transform(current) != current) {
        updateHeaderSettings(transform)
    }
}

private suspend fun IWidgetSettingsRepository.updateEventIfChanged(transform: (EventSettings) -> EventSettings) {
    val current: EventSettings = eventSettings.first()
    if (transform(current) != current) {
        updateEventSettings(transform)
    }
}

private suspend fun IWidgetSettingsRepository.updateWidgetAreaIfChanged(transform: (WidgetAreaSettings) -> WidgetAreaSettings) {
    val current: WidgetAreaSettings = widgetAreaSettings.first()
    if (transform(current) != current) {
        updateWidgetAreaSettings(transform)
    }
}
