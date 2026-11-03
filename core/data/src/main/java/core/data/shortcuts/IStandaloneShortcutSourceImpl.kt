package core.data.shortcuts

import android.os.UserHandle
import core.data.profiles.IProfileSource
import core.data.room.dao.StandaloneShortcutDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Resolves the stored Standalone shortcut keys through [shortcutSource] as they change.
 *
 * A Paused Profile cannot publish its Shortcuts, so a stored key from one keeps its last resolved
 * Shortcut, or a placeholder until the Profile lists it, and returns to live resolution on resume.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IStandaloneShortcutSourceImpl(
    private val standaloneShortcutDao: StandaloneShortcutDao,
    private val shortcutSource: IShortcutSource,
    private val profileSource: IProfileSource
) : IStandaloneShortcutSource {
    override val shortcuts: Flow<StandaloneShortcuts> = flow {
        val resolvedShortcuts: MutableMap<ShortcutKey, ShortcutLaunchable> = mutableMapOf()
        combine(
            standaloneShortcutDao.observeAll(),
            profileSource.profiles.map { profiles ->
                profiles.filter { profile -> profile.isQuiet }.mapTo(mutableSetOf()) { profile -> profile.user }
            }
        ) { records, quietProfiles -> records to quietProfiles }
            .flatMapLatest { (records, quietProfiles) ->
                val storedShortcuts: List<Pair<ShortcutKey, String>> = records.mapNotNull { record ->
                    ShortcutKey.parse(record.key)?.let { key -> key to record.key }
                }
                val keys: Set<ShortcutKey> = storedShortcuts.mapTo(mutableSetOf()) { (key, _) -> key }
                resolvedShortcuts.keys.retainAll(keys)
                shortcutSource.resolve(keys).map { resolution ->
                    resolution.shortcuts.forEach { (key, shortcut) -> resolvedShortcuts[key] = shortcut }
                    resolution.goneKeys.forEach { key -> resolvedShortcuts.remove(key) }
                    resolveStoredShortcuts(
                        storedShortcuts = storedShortcuts,
                        resolution = resolution,
                        quietProfiles = quietProfiles,
                        resolvedShortcuts = resolvedShortcuts
                    )
                }
            }
            .distinctUntilChanged()
            .collect { shortcuts -> emit(shortcuts) }
    }

    private fun resolveStoredShortcuts(
        storedShortcuts: List<Pair<ShortcutKey, String>>,
        resolution: ShortcutResolution,
        quietProfiles: Set<UserHandle>,
        resolvedShortcuts: Map<ShortcutKey, ShortcutLaunchable>
    ): StandaloneShortcuts {
        val launchables: MutableList<ShortcutLaunchable> = mutableListOf()
        val unresolvedKeys: MutableSet<String> = mutableSetOf()
        storedShortcuts.forEach { (key, storedKey) ->
            val shortcut: ShortcutLaunchable? = resolution.shortcuts[key] ?: key.pausedShortcut(
                quietProfiles = quietProfiles,
                resolvedShortcuts = resolvedShortcuts
            )
            when {
                shortcut != null -> launchables += shortcut
                key !in resolution.goneKeys -> unresolvedKeys += storedKey
            }
        }
        return StandaloneShortcuts(launchables = launchables, unresolvedKeys = unresolvedKeys)
    }

    private fun ShortcutKey.pausedShortcut(
        quietProfiles: Set<UserHandle>,
        resolvedShortcuts: Map<ShortcutKey, ShortcutLaunchable>
    ): ShortcutLaunchable? {
        if (profile !in quietProfiles) {
            return null
        }
        return resolvedShortcuts[this] ?: ShortcutLaunchable(
            packageName = packageName,
            shortcutId = id,
            profile = profile,
            shortLabel = packageName,
            longLabel = packageName,
            isPlaceholder = true
        )
    }
}
