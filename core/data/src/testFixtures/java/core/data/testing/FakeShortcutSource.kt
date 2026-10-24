package core.data.testing

import android.os.UserHandle
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutsResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A settleable [IShortcutSource] over one shared result. */
class FakeShortcutSource(initialResult: ShortcutsResult = ShortcutsResult.Available(shortcuts = emptyList())) : IShortcutSource {
    private val result: MutableStateFlow<ShortcutsResult> = MutableStateFlow(initialResult)

    /** The packages and profiles whose shortcuts were requested, in request order. */
    val requests: MutableList<Pair<String, UserHandle>> = mutableListOf()

    override fun shortcuts(packageName: String, profile: UserHandle): Flow<ShortcutsResult> {
        requests += packageName to profile
        return result.asStateFlow()
    }

    fun setResult(result: ShortcutsResult) {
        this.result.value = result
    }
}
