package core.data.testing

import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.prefs.withBatteryVisibility
import core.data.prefs.withClockVisible
import core.data.prefs.withDateVisible
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.MediaPlayerSettings
import core.data.widgets.WidgetAreaSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** A MockK [IWidgetSettingsRepository] over the given initial settings. */
fun createWidgetSettingsRepositoryMock(
    headerSettings: HeaderSettings = HeaderSettings(),
    eventSettings: EventSettings = EventSettings(),
    widgetAreaSettings: WidgetAreaSettings = WidgetAreaSettings.Default,
    mediaPlayerSettings: MediaPlayerSettings = MediaPlayerSettings.Default
): IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
    headerSettings = MutableStateFlow(headerSettings),
    eventSettings = MutableStateFlow(eventSettings),
    widgetAreaSettings = MutableStateFlow(widgetAreaSettings),
    mediaPlayerSettings = MutableStateFlow(mediaPlayerSettings)
)

/**
 * A MockK [IWidgetSettingsRepository] reading and writing [headerSettings], [eventSettings],
 * [widgetAreaSettings] and [mediaPlayerSettings]. Every member is stubbed: the update methods apply the
 * caller's transform and the setters apply the repository's stored rules.
 */
fun createWidgetSettingsRepositoryMock(
    headerSettings: MutableStateFlow<HeaderSettings>,
    eventSettings: MutableStateFlow<EventSettings> = MutableStateFlow(EventSettings()),
    widgetAreaSettings: MutableStateFlow<WidgetAreaSettings> = MutableStateFlow(WidgetAreaSettings.Default),
    mediaPlayerSettings: MutableStateFlow<MediaPlayerSettings> = MutableStateFlow(MediaPlayerSettings.Default)
): IWidgetSettingsRepository {
    val mockWidgetSettingsRepository: IWidgetSettingsRepository = mockk()
    every { mockWidgetSettingsRepository.headerSettings } returns headerSettings
    every { mockWidgetSettingsRepository.eventSettings } returns eventSettings
    every { mockWidgetSettingsRepository.widgetAreaSettings } returns widgetAreaSettings
    every { mockWidgetSettingsRepository.mediaPlayerSettings } returns mediaPlayerSettings
    coEvery { mockWidgetSettingsRepository.updateHeaderSettings(any()) } answers {
        headerSettings.update { settings -> firstArg<(HeaderSettings) -> HeaderSettings>()(settings) }
    }
    coEvery { mockWidgetSettingsRepository.updateEventSettings(any()) } answers {
        eventSettings.update { settings -> firstArg<(EventSettings) -> EventSettings>()(settings) }
    }
    coEvery { mockWidgetSettingsRepository.updateWidgetAreaSettings(any()) } answers {
        widgetAreaSettings.update { settings -> firstArg<(WidgetAreaSettings) -> WidgetAreaSettings>()(settings) }
    }
    coEvery { mockWidgetSettingsRepository.updateMediaPlayerSettings(any()) } answers {
        mediaPlayerSettings.update { settings -> firstArg<(MediaPlayerSettings) -> MediaPlayerSettings>()(settings) }
    }
    coEvery { mockWidgetSettingsRepository.setMaxStackPages(any()) } answers {
        widgetAreaSettings.update { settings ->
            settings.copy(
                maxStackPages = firstArg<Int>().coerceIn(
                    WidgetAreaSettings.MIN_STACK_PAGES,
                    WidgetAreaSettings.MAX_STACK_PAGES
                )
            )
        }
    }
    coEvery { mockWidgetSettingsRepository.setStackIndicatorVisible(any()) } answers {
        widgetAreaSettings.update { settings -> settings.copy(stackIndicatorVisible = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.setHeightLimitEnabled(any()) } answers {
        widgetAreaSettings.update { settings -> settings.copy(heightLimitEnabled = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.setHeightLimit(any()) } answers {
        widgetAreaSettings.update { settings -> settings.copy(heightLimit = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.setTopOffsetDp(any()) } answers {
        widgetAreaSettings.update { settings ->
            settings.copy(topOffsetDp = WidgetAreaSettings.snapOffsetToStep(offsetDp = firstArg()))
        }
    }
    coEvery { mockWidgetSettingsRepository.setWidgetSpacing(any()) } answers {
        widgetAreaSettings.update { settings -> settings.copy(widgetSpacing = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.setClockVisible(any()) } answers {
        headerSettings.update { settings -> settings.withClockVisible(visible = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.setDateVisible(any()) } answers {
        headerSettings.update { settings -> settings.withDateVisible(visible = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.setBatteryVisibility(any()) } answers {
        headerSettings.update { settings -> settings.withBatteryVisibility(visibility = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.setEventsEnabled(any()) } answers {
        eventSettings.update { settings -> settings.copy(enabled = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.setEventLookaheadHours(any()) } answers {
        eventSettings.update { settings -> settings.copy(lookaheadHours = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.setEventMaxEvents(any()) } answers {
        eventSettings.update { settings -> settings.copy(maxEvents = firstArg()).withValidMaxEvents() }
    }
    coEvery { mockWidgetSettingsRepository.setEventIncludeAllDay(any()) } answers {
        eventSettings.update { settings -> settings.copy(includeAllDay = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.setExcludedCalendarIds(any()) } answers {
        eventSettings.update { settings -> settings.copy(excludedCalendarIds = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.pruneExcludedCalendarIds(any()) } answers {
        val inputAvailableCalendarIds: Set<String> = firstArg()
        if (inputAvailableCalendarIds.isNotEmpty()) {
            eventSettings.update { settings ->
                settings.copy(excludedCalendarIds = settings.excludedCalendarIds.intersect(inputAvailableCalendarIds))
            }
        }
    }
    coEvery { mockWidgetSettingsRepository.setMediaPlayerEnabled(any()) } answers {
        mediaPlayerSettings.update { settings -> settings.copy(enabled = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.setMediaPlayerShowWhenPaused(any()) } answers {
        mediaPlayerSettings.update { settings -> settings.copy(showWhenPaused = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.setMediaPlayerShowArtwork(any()) } answers {
        mediaPlayerSettings.update { settings -> settings.copy(showArtwork = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.setMediaPlayerPlacement(any()) } answers {
        mediaPlayerSettings.update { settings -> settings.copy(placement = firstArg()) }
    }
    coEvery { mockWidgetSettingsRepository.setMediaPlayerPackageExcluded(any(), any()) } answers {
        val inputPackageName: String = firstArg()
        val inputExcluded: Boolean = arg(1)
        mediaPlayerSettings.update { settings ->
            settings.copy(
                excludedPackages = if (inputExcluded) {
                    settings.excludedPackages + inputPackageName
                } else {
                    settings.excludedPackages - inputPackageName
                }
            )
        }
    }
    return mockWidgetSettingsRepository
}
