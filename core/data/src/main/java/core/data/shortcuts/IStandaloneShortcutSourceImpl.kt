package core.data.shortcuts

import core.data.room.dao.StandaloneShortcutDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Resolves the stored Standalone shortcut keys through [shortcutSource] as they change. */
@OptIn(ExperimentalCoroutinesApi::class)
class IStandaloneShortcutSourceImpl(private val standaloneShortcutDao: StandaloneShortcutDao, private val shortcutSource: IShortcutSource) :
    IStandaloneShortcutSource {
    override val shortcuts: Flow<StandaloneShortcuts> = standaloneShortcutDao.observeAll()
        .flatMapLatest { records ->
            val storedShortcuts: List<Pair<ShortcutKey, String>> = records.mapNotNull { record ->
                ShortcutKey.parse(record.key)?.let { key -> key to record.key }
            }
            val keys: Set<ShortcutKey> = storedShortcuts.mapTo(mutableSetOf()) { (key, _) -> key }
            shortcutSource.resolve(keys).map { resolution ->
                StandaloneShortcuts(
                    launchables = storedShortcuts.mapNotNull { (key, _) -> resolution.shortcuts[key] },
                    unresolvedKeys = storedShortcuts
                        .filter { (key, _) -> key !in resolution.shortcuts && key !in resolution.goneKeys }
                        .mapTo(mutableSetOf()) { (_, storedKey) -> storedKey }
                )
            }
        }
        .distinctUntilChanged()
}
