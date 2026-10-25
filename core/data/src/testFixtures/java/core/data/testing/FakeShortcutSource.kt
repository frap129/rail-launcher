package core.data.testing

import android.os.UserHandle
import core.data.shortcuts.AppShortcuts
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/** One pin call made through [FakeShortcutSource]. */
data class PinCall(val packageName: String, val profile: UserHandle, val ids: Set<String>)

/** A settleable [IShortcutSource] over one shared result and one resolved map. */
class FakeShortcutSource(initialResult: ShortcutsResult = ShortcutsResult.Available(shortcuts = emptyList())) : IShortcutSource {
    private val result: MutableStateFlow<ShortcutsResult> = MutableStateFlow(initialResult)
    private val apps: MutableStateFlow<List<AppShortcuts>> = MutableStateFlow(emptyList())
    private val resolved: MutableStateFlow<Map<ShortcutKey, ShortcutLaunchable?>?> = MutableStateFlow(emptyMap())

    /** The packages and profiles whose shortcuts were requested, in request order. */
    val requests: MutableList<Pair<String, UserHandle>> = mutableListOf()

    /** Every pin call, in order. */
    val pinCalls: MutableList<PinCall> = mutableListOf()

    override fun shortcuts(packageName: String, profile: UserHandle): Flow<ShortcutsResult> {
        requests += packageName to profile
        return result.asStateFlow()
    }

    override fun appsWithShortcuts(): Flow<List<AppShortcuts>> = apps.asStateFlow()

    override fun resolve(keys: Set<ShortcutKey>): Flow<Map<ShortcutKey, ShortcutLaunchable?>> = resolved.map { shortcuts ->
        shortcuts?.let { map ->
            keys.associateWith { key -> map[key] }
        }.orEmpty()
    }

    override suspend fun pin(packageName: String, profile: UserHandle, ids: Set<String>) {
        pinCalls += PinCall(packageName = packageName, profile = profile, ids = ids)
    }

    fun setResult(result: ShortcutsResult) {
        this.result.value = result
    }

    fun setAppsWithShortcuts(apps: List<AppShortcuts>) {
        this.apps.value = apps
    }

    fun setResolvedShortcuts(shortcuts: Map<ShortcutKey, ShortcutLaunchable?>) {
        resolved.value = shortcuts
    }

    /** Makes [resolve] report every key as unknown, as when Rail is not the default launcher. */
    fun setResolveUnavailable() {
        resolved.value = null
    }
}
