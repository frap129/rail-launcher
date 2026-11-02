package core.data.testing

import core.data.shortcuts.AppShortcuts
import core.data.shortcuts.IShortcutPickerSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A MockK [IShortcutPickerSource] over [appsWithShortcuts]. */
fun createShortcutPickerSourceMock(
    appsWithShortcuts: StateFlow<List<AppShortcuts>> = MutableStateFlow(emptyList())
): IShortcutPickerSource {
    val mockShortcutPickerSource: IShortcutPickerSource = mockk()
    every { mockShortcutPickerSource.appsWithShortcuts() } returns appsWithShortcuts
    return mockShortcutPickerSource
}
