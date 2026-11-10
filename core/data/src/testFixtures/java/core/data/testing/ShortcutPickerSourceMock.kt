package core.data.testing

import core.data.search.IShortcutSearchSource
import core.data.search.SearchShortcut
import core.data.shortcuts.AppShortcuts
import core.data.shortcuts.IShortcutPickerSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A MockK [IShortcutSearchSource] over [shortcuts]. */
fun createShortcutSearchSourceMock(shortcuts: StateFlow<List<SearchShortcut>> = MutableStateFlow(emptyList())): IShortcutSearchSource {
    val mockShortcutSearchSource: IShortcutSearchSource = mockk()
    every { mockShortcutSearchSource.shortcuts } returns shortcuts
    return mockShortcutSearchSource
}

/** A MockK [IShortcutPickerSource] over [appsWithShortcuts]. */
fun createShortcutPickerSourceMock(
    appsWithShortcuts: StateFlow<List<AppShortcuts>> = MutableStateFlow(emptyList())
): IShortcutPickerSource {
    val mockShortcutPickerSource: IShortcutPickerSource = mockk()
    every { mockShortcutPickerSource.appsWithShortcuts() } returns appsWithShortcuts
    return mockShortcutPickerSource
}
