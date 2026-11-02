package core.data.shortcuts

import core.data.apps.model.App
import core.data.launchables.ILaunchableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Lists the Shortcuts every App publishes by reading the launchable list through [launchableRepository]. */
class IShortcutPickerSourceImpl(private val shortcutSource: IShortcutSource, private val launchableRepository: ILaunchableRepository) :
    IShortcutPickerSource {
    override fun appsWithShortcuts(): Flow<List<AppShortcuts>> = launchableRepository.launchableState
        .map { state -> state.launchables.filterIsInstance<App>().filter { app -> app.isPackageRepresentative } }
        .distinctUntilChanged()
        .flatMapLatest { apps ->
            if (apps.isEmpty()) {
                flowOf(emptyList())
            } else {
                val appFlows: List<Flow<AppShortcuts>> = apps.map { app ->
                    shortcutSource.shortcuts(packageName = app.packageName, profile = app.profile)
                        .map { result -> AppShortcuts(app = app, result = result) }
                }
                combine(appFlows) { results -> results.toList() }
            }
        }
}
