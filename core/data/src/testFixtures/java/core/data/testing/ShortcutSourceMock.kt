package core.data.testing

import android.os.UserHandle
import core.data.shortcuts.AppShortcuts
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutResolution
import core.data.shortcuts.ShortcutsResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** One pin call made through an [IShortcutSource] test double. */
data class PinCall(val packageName: String, val profile: UserHandle, val ids: Set<String>)

/** A MockK [IShortcutSource] over [resolution], [shortcutsResult] and [appsWithShortcuts], recording every pin in [pinCalls]. */
fun createShortcutSourceMock(
    resolution: StateFlow<ShortcutResolution?> = MutableStateFlow(ShortcutResolution()),
    pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList()),
    shortcutsResult: StateFlow<ShortcutsResult> = MutableStateFlow(ShortcutsResult.Available(shortcuts = emptyList())),
    appsWithShortcuts: StateFlow<List<AppShortcuts>> = MutableStateFlow(emptyList())
): IShortcutSource {
    val mockShortcutSource: IShortcutSource = mockk()
    every { mockShortcutSource.shortcuts(any(), any()) } returns shortcutsResult
    every { mockShortcutSource.appsWithShortcuts() } returns appsWithShortcuts
    every { mockShortcutSource.resolve(any()) } answers {
        val keys: Set<ShortcutKey> = firstArg()
        resolution.map { current -> current.filterToKeys(keys = keys) }
    }
    coEvery { mockShortcutSource.pin(any(), any(), any()) } coAnswers {
        val pin: PinCall = PinCall(packageName = firstArg(), profile = arg(1), ids = arg(2))
        pinCalls.update { pins -> pins + pin }
    }
    return mockShortcutSource
}

/** Waits until [expectedPin] has been recorded, skipping any pin that arrives earlier. */
suspend fun StateFlow<List<PinCall>>.awaitPin(expectedPin: PinCall) {
    first { pins -> expectedPin in pins }
}

internal fun ShortcutResolution?.filterToKeys(keys: Set<ShortcutKey>): ShortcutResolution = this?.let { resolution ->
    ShortcutResolution(
        shortcuts = resolution.shortcuts.filterKeys { key -> key in keys },
        goneKeys = resolution.goneKeys.filterTo(mutableSetOf()) { key -> key in keys }
    )
} ?: ShortcutResolution()
