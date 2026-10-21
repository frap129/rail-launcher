package core.data.widgets

import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting
import core.data.room.dao.HeaderSettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class TapTargetCleanup(val settings: HeaderSettings, val validKeys: Set<String>, val hadStaleTargets: Boolean)

internal fun HeaderSettings.planTapTargetCleanup(launchables: List<Launchable>): TapTargetCleanup {
    val keys: Set<String> = launchables.mapTo(mutableSetOf()) { launchable -> launchable.key }
    val hadStaleTargets: Boolean = timeTapTarget.isStale(keys) || dateTapTarget.isStale(keys)
    return TapTargetCleanup(
        settings = withoutStaleTapTargets(keys),
        validKeys = keys,
        hadStaleTargets = hadStaleTargets
    )
}

internal fun HeaderSettings.withoutStaleTapTargets(keys: Set<String>): HeaderSettings = copy(
    timeTapTarget = timeTapTarget.takeUnless { setting -> setting.isStale(keys) },
    dateTapTarget = dateTapTarget.takeUnless { setting -> setting.isStale(keys) }
)

private fun HeaderTapSetting?.isStale(keys: Set<String>): Boolean = this is HeaderTapSetting.Launchable && key !in keys

class IWidgetSettingsRepositoryImpl(private val headerSettingsDao: HeaderSettingsDao, launchableRepository: ILaunchableRepository) :
    IWidgetSettingsRepository {
    private val cleanupMutex: Mutex = Mutex()

    // Deleting stale keys keeps a reinstalled Launchable from restoring an old tap action.
    override val headerSettings: Flow<HeaderSettings> = combine(
        headerSettingsDao.observe(),
        launchableRepository.launchableState
    ) { settings, state ->
        if (state.isLoaded) {
            settings.planTapTargetCleanup(state.launchables)
        } else {
            TapTargetCleanup(settings = settings, validKeys = emptySet(), hadStaleTargets = false)
        }
    }
        .onEach { cleanup ->
            if (cleanup.hadStaleTargets) {
                cleanupMutex.withLock {
                    // Recompute against the current row so a choice saved meanwhile is not clobbered.
                    headerSettingsDao.updateIfChanged { settings ->
                        settings.withoutStaleTapTargets(keys = cleanup.validKeys)
                    }
                }
            }
        }
        .map { cleanup -> cleanup.settings }
        .distinctUntilChanged()

    override suspend fun updateHeaderSettings(transform: (HeaderSettings) -> HeaderSettings) {
        headerSettingsDao.update(transform)
    }
}
