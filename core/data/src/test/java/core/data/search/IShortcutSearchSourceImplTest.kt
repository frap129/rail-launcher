package core.data.search

import android.content.ComponentName
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.LaunchableGroup
import core.data.popups.IPopupRepository
import core.data.shortcuts.IShortcutPickerSourceImpl
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.data.testing.PopupRepositoryState
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createPausedWorkApp
import core.data.testing.createPopupRepositoryMock
import core.data.testing.createPrivateApp
import core.data.testing.createShortcut
import core.data.testing.createShortcutSourceMock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IShortcutSearchSourceImplTest {
    private val inputPublisher: App = createApp(name = "Messages", packageName = "com.example.messages")
    private val inputShortcut: ShortcutLaunchable = createShortcut(
        packageName = inputPublisher.packageName,
        longLabel = "New message"
    )
    private val inputShortcutsResult: MutableStateFlow<ShortcutsResult> = MutableStateFlow(
        ShortcutsResult.Available(shortcuts = listOf(inputShortcut))
    )
    private val mockShortcutSource: IShortcutSource = createShortcutSourceMock(shortcutsResult = inputShortcutsResult)
    private val mockPopupRepositoryState: PopupRepositoryState = PopupRepositoryState()
    private val mockPopupRepository: IPopupRepository = createPopupRepositoryMock(state = mockPopupRepositoryState)

    @Test
    fun `offers the Shortcuts of an App with its publisher name`() = runTest {
        val actualShortcuts: List<SearchShortcut> = createSource().shortcuts.first()

        val expectedShortcuts: List<SearchShortcut> = listOf(
            SearchShortcut(
                shortcut = inputShortcut.copy(profileInfo = inputPublisher.profileInfo),
                publisherName = "Messages",
                isHidden = false
            )
        )
        assertEquals(expectedShortcuts, actualShortcuts)
    }

    @Test
    fun `offers no Shortcuts while they are unavailable`() = runTest {
        inputShortcutsResult.value = ShortcutsResult.Unavailable

        val actualShortcuts: List<SearchShortcut> = createSource().shortcuts.first()

        assertTrue(actualShortcuts.isEmpty())
    }

    @Test
    fun `offers no Shortcuts of a paused Profile that was never available`() = runTest {
        inputShortcutsResult.value = ShortcutsResult.ProfilePaused

        val actualShortcuts: List<SearchShortcut> = createSource().shortcuts.first()

        assertTrue(actualShortcuts.isEmpty())
    }

    @Test
    fun `keeps a paused Profile's last Shortcuts with its paused Profile`() = runTest {
        val inputPausedPublisher: App = createPausedWorkApp()
        val inputPausedShortcut: ShortcutLaunchable = createShortcut(
            packageName = inputPausedPublisher.packageName,
            profile = inputPausedPublisher.profile,
            longLabel = "Compose"
        )
        val inputPausedResult: MutableStateFlow<ShortcutsResult> = MutableStateFlow(
            ShortcutsResult.Available(shortcuts = listOf(inputPausedShortcut))
        )
        val source: IShortcutSearchSource = createSource(
            launchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputPausedPublisher)),
            mockShortcutSource = createShortcutSourceMock(shortcutsResult = inputPausedResult)
        )

        source.shortcuts.test {
            val actualAvailable: List<SearchShortcut> = awaitItem()
            assertEquals(1, actualAvailable.size)

            inputPausedResult.value = ShortcutsResult.ProfilePaused

            val actualPaused: List<SearchShortcut> = awaitItem()
            val expectedShortcuts: List<SearchShortcut> = listOf(
                SearchShortcut(
                    shortcut = inputPausedShortcut.copy(profileInfo = inputPausedPublisher.profileInfo),
                    publisherName = inputPausedPublisher.name,
                    isHidden = false
                )
            )
            assertEquals(expectedShortcuts, actualPaused)
            assertTrue(actualPaused.single().shortcut.isPaused)
        }
    }

    @Test
    fun `offers Shortcuts for the Package representative only`() = runTest {
        val inputOtherActivity: App = inputPublisher.copy(
            componentName = ComponentName(inputPublisher.packageName, "com.example.messages.SettingsActivity"),
            name = "Other",
            defaultName = "Other",
            isPackageRepresentative = false
        )

        val actualShortcuts: List<SearchShortcut> = createSource(
            launchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputPublisher, inputOtherActivity))
        ).shortcuts.first()

        val expectedShortcuts: List<SearchShortcut> = listOf(
            SearchShortcut(
                shortcut = inputShortcut.copy(profileInfo = inputPublisher.profileInfo),
                publisherName = "Messages",
                isHidden = false
            )
        )
        assertEquals(expectedShortcuts, actualShortcuts)
    }

    @Test
    fun `marks a Shortcut hidden in its App's Pop-up`() = runTest {
        mockPopupRepositoryState.hiddenIdsByOwner.value = mapOf(inputPublisher.key to setOf(inputShortcut.shortcutId))

        val actualShortcuts: List<SearchShortcut> = createSource().shortcuts.first()

        val expectedShortcuts: List<SearchShortcut> = listOf(
            SearchShortcut(
                shortcut = inputShortcut.copy(profileInfo = inputPublisher.profileInfo),
                publisherName = "Messages",
                isHidden = true
            )
        )
        assertEquals(expectedShortcuts, actualShortcuts)
    }

    @Test
    fun `offers no Shortcuts while a private App's Private Space is Locked`() = runTest {
        val inputPrivatePublisher: App = createPrivateApp()
        val inputPrivateShortcut: ShortcutLaunchable = createShortcut(
            packageName = inputPrivatePublisher.packageName,
            profile = inputPrivatePublisher.profile,
            longLabel = "Secret action"
        )
        val inputPrivateResult: MutableStateFlow<ShortcutsResult> = MutableStateFlow(
            ShortcutsResult.Available(shortcuts = listOf(inputPrivateShortcut))
        )
        val inputLaunchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = listOf(LaunchableGroup.PrivateSpace.Unlocked(items = listOf(inputPrivatePublisher))))
        )
        val source: IShortcutSearchSource = createSource(
            launchableRepository = createLaunchableRepositoryMock(state = inputLaunchableState),
            mockShortcutSource = createShortcutSourceMock(shortcutsResult = inputPrivateResult)
        )

        source.shortcuts.test {
            assertEquals(1, awaitItem().size)

            inputLaunchableState.value = createLaunchableState(groups = listOf(LaunchableGroup.PrivateSpace.Locked))

            assertTrue(awaitItem().isEmpty())
        }
    }

    private fun createSource(
        launchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputPublisher)),
        mockShortcutSource: IShortcutSource = this.mockShortcutSource
    ): IShortcutSearchSource = IShortcutSearchSourceImpl(
        shortcutPickerSource = IShortcutPickerSourceImpl(
            shortcutSource = mockShortcutSource,
            launchableRepository = launchableRepository
        ),
        popupRepository = mockPopupRepository
    )
}
