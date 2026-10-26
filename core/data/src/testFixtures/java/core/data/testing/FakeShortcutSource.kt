package core.data.testing

import android.os.UserHandle
import core.data.shortcuts.AppShortcuts
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutResolution
import core.data.shortcuts.ShortcutsResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/** One pin call made through [FakeShortcutSource]. */
data class PinCall(val packageName: String, val profile: UserHandle, val ids: Set<String>)

/** A settleable [IShortcutSource] over one shared result and one resolution. */
class FakeShortcutSource(initialResult: ShortcutsResult = ShortcutsResult.Available(shortcuts = emptyList())) : IShortcutSource {
    private val result: MutableStateFlow<ShortcutsResult> = MutableStateFlow(initialResult)
    private val apps: MutableStateFlow<List<AppShortcuts>> = MutableStateFlow(emptyList())
    private val resolution: MutableStateFlow<ShortcutResolution?> = MutableStateFlow(ShortcutResolution())

    /** The packages and profiles whose shortcuts were requested, in request order. */
    val requests: MutableList<Pair<String, UserHandle>> = mutableListOf()

    /** Every pin call, in order. */
    val pinCalls: MutableList<PinCall> = mutableListOf()

    override fun shortcuts(packageName: String, profile: UserHandle): Flow<ShortcutsResult> {
        requests += packageName to profile
        return result.asStateFlow()
    }

    override fun appsWithShortcuts(): Flow<List<AppShortcuts>> = apps.asStateFlow()

    override fun resolve(keys: Set<ShortcutKey>): Flow<ShortcutResolution> = resolution.map { current ->
        current?.let { currentResolution ->
            ShortcutResolution(
                shortcuts = currentResolution.shortcuts.filterKeys { key -> key in keys },
                goneKeys = currentResolution.goneKeys.filterTo(mutableSetOf()) { key -> key in keys }
            )
        } ?: ShortcutResolution()
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

    /** Sets what [resolve] reports: the Shortcuts it finds and the keys it reports gone. */
    fun setResolution(shortcuts: Map<ShortcutKey, ShortcutLaunchable> = emptyMap(), goneKeys: Set<ShortcutKey> = emptySet()) {
        resolution.value = ShortcutResolution(shortcuts = shortcuts, goneKeys = goneKeys)
    }

    /** Makes [resolve] report every key as unknown, as when Rail is not the default launcher. */
    fun setResolveUnavailable() {
        resolution.value = null
    }
}
