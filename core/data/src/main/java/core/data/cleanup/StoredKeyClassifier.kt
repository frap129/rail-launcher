package core.data.cleanup

import core.data.apps.model.App
import core.data.apps.model.AppProfile
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.isKeptKey
import core.data.launchables.model.Launchable
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutResolution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** What Rail knows about one stored key. */
sealed interface StoredKeyState {
    /** The key resolves to [launchable]; a Shortcut resolves to its [ShortcutLaunchable]. */
    data class Present(val launchable: Launchable) : StoredKeyState

    /** A Kept Profile owns the key, so Rail keeps it until the Profile lists its Apps or is removed. */
    data object Kept : StoredKeyState

    /** The key's App or Profile still exists, but its Shortcut cannot be read right now. */
    data object Unresolved : StoredKeyState

    /** The key's Launchable, Shortcut or Profile no longer exists. */
    data object Stale : StoredKeyState
}

/** The shared classification of stored Launchable and Shortcut keys. */
interface IStoredKeyClassifier {
    /**
     * The state of every key in [keys], updated when the Launchables or Shortcuts change.
     * Emits null until the Launchables load.
     */
    fun observe(keys: Flow<Set<String>>): Flow<Map<String, StoredKeyState>?>
}

/** Classifies stored keys with the Loaded Launchables and the platform's Shortcut resolution. */
@OptIn(ExperimentalCoroutinesApi::class)
class IStoredKeyClassifierImpl(private val launchableRepository: ILaunchableRepository, private val shortcutSource: IShortcutSource) :
    IStoredKeyClassifier {
    // Resolves the Shortcut keys once and reclassifies with each Launchable change, so renames,
    // icon pack changes and Folder edits do not restart the platform query.
    override fun observe(keys: Flow<Set<String>>): Flow<Map<String, StoredKeyState>?> = flow {
        coroutineScope {
            val requestedKeys: Flow<Set<String>> = shareLatestValue(upstream = keys)
            emitAll(
                requestedKeys
                    .map { currentKeys ->
                        currentKeys.mapNotNullTo(mutableSetOf()) { key -> ShortcutKey.parse(key) }
                    }
                    .distinctUntilChanged()
                    .flatMapLatest { shortcutKeys ->
                        combine(
                            launchableRepository.launchableState,
                            requestedKeys,
                            shortcutSource.resolve(shortcutKeys)
                        ) { state, currentKeys, resolution ->
                            if (state.isLoaded) classify(state = state, keys = currentKeys, resolution = resolution) else null
                        }
                    }
                    .distinctUntilChanged()
            )
        }
    }

    private fun classify(state: LaunchableState, keys: Set<String>, resolution: ShortcutResolution): Map<String, StoredKeyState> {
        val installedApps: Set<AppProfile> = state.launchables
            .filterIsInstance<App>()
            .mapTo(mutableSetOf()) { app -> AppProfile(packageName = app.packageName, profile = app.profile) }
        return keys.associateWith { key ->
            classifyKey(key = key, state = state, installedApps = installedApps, resolution = resolution)
        }
    }

    private fun classifyKey(
        key: String,
        state: LaunchableState,
        installedApps: Set<AppProfile>,
        resolution: ShortcutResolution
    ): StoredKeyState {
        if (state.isKeptKey(key)) {
            return StoredKeyState.Kept
        }
        if (key in state.retainedKeys) {
            return StoredKeyState.Unresolved
        }
        val shortcutKey: ShortcutKey? = ShortcutKey.parse(key)
        if (shortcutKey != null) {
            val shortcut: ShortcutLaunchable? = resolution.shortcuts[shortcutKey]
            return when {
                shortcut != null -> StoredKeyState.Present(launchable = shortcut)
                shortcutKey in resolution.goneKeys -> StoredKeyState.Stale
                shortcutKey.toAppProfile() in installedApps -> StoredKeyState.Unresolved
                else -> StoredKeyState.Stale
            }
        }
        val launchable: Launchable? = state.launchableFor(key)
        return if (launchable == null) StoredKeyState.Stale else StoredKeyState.Present(launchable = launchable)
    }
}
