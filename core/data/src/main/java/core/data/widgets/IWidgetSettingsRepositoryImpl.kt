package core.data.widgets

import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.prefs.isRemovable
import core.data.room.dao.EventSettingsDao
import core.data.room.dao.HeaderSettingsDao
import core.data.room.dao.MediaPlayerSettingsDao
import core.data.room.dao.WidgetAreaSettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class TapTargetCleanup(val settings: HeaderSettings, val state: LaunchableState, val hadStaleTargets: Boolean)

internal fun HeaderSettings.planTapTargetCleanup(state: LaunchableState): TapTargetCleanup {
    val hadStaleTargets: Boolean = timeTapTarget.isRemovable(state) || dateTapTarget.isRemovable(state)
    return TapTargetCleanup(
        settings = withoutStaleTapTargets(state),
        state = state,
        hadStaleTargets = hadStaleTargets
    )
}

internal fun HeaderSettings.withoutStaleTapTargets(state: LaunchableState): HeaderSettings = copy(
    timeTapTarget = timeTapTarget.takeUnless { setting -> setting.isRemovable(state) },
    dateTapTarget = dateTapTarget.takeUnless { setting -> setting.isRemovable(state) }
)

class IWidgetSettingsRepositoryImpl(
    private val headerSettingsDao: HeaderSettingsDao,
    private val eventSettingsDao: EventSettingsDao,
    private val widgetAreaSettingsDao: WidgetAreaSettingsDao,
    private val mediaPlayerSettingsDao: MediaPlayerSettingsDao,
    launchableRepository: ILaunchableRepository
) : IWidgetSettingsRepository {
    private val cleanupMutex: Mutex = Mutex()

    // Deleting stale keys keeps a reinstalled Launchable from restoring an old tap action.
    override val headerSettings: Flow<HeaderSettings> = combine(
        headerSettingsDao.observe(),
        launchableRepository.launchableState
    ) { settings, state ->
        if (state.isLoaded) {
            settings.planTapTargetCleanup(state)
        } else {
            TapTargetCleanup(settings = settings, state = state, hadStaleTargets = false)
        }
    }
        .onEach { cleanup ->
            if (cleanup.hadStaleTargets) {
                cleanupMutex.withLock {
                    // Recompute against the current row so a choice saved meanwhile is not clobbered.
                    headerSettingsDao.updateIfChanged { settings ->
                        settings.withoutStaleTapTargets(state = cleanup.state)
                    }
                }
            }
        }
        .map { cleanup -> cleanup.settings }
        .distinctUntilChanged()

    override val eventSettings: Flow<EventSettings> = eventSettingsDao.observe().distinctUntilChanged()

    override val widgetAreaSettings: Flow<WidgetAreaSettings> = widgetAreaSettingsDao.observe().distinctUntilChanged()

    override val mediaPlayerSettings: Flow<MediaPlayerSettings> = mediaPlayerSettingsDao.observe().distinctUntilChanged()

    override suspend fun updateHeaderSettings(transform: (HeaderSettings) -> HeaderSettings) {
        headerSettingsDao.update(transform)
    }

    override suspend fun updateEventSettings(transform: (EventSettings) -> EventSettings) {
        eventSettingsDao.update(transform)
    }

    override suspend fun updateWidgetAreaSettings(transform: (WidgetAreaSettings) -> WidgetAreaSettings) {
        widgetAreaSettingsDao.update(transform)
    }

    override suspend fun updateMediaPlayerSettings(transform: (MediaPlayerSettings) -> MediaPlayerSettings) {
        mediaPlayerSettingsDao.update(transform)
    }
}
