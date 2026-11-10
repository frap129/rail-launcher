package core.data.search

import core.data.apps.model.App
import core.data.folders.Folder
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createBuiltinGroup
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createPausedWorkApp
import core.data.testing.createPrivateApp
import core.data.testing.createRailLaunchable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class LaunchableSearchTest {
    @Test
    fun `folds accents and case in the query and the name`() {
        val inputCafe: Launchable = createRailLaunchable("Café")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputCafe))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "CAFE")

        assertEquals(listOf(inputCafe), actualResults)
    }

    @Test
    fun `ranks an exact name above a name start and a word start`() {
        val inputExact: Launchable = createRailLaunchable("Maps")
        val inputNameStart: Launchable = createRailLaunchable("Maps Pro")
        val inputWordStart: Launchable = createRailLaunchable("Google Maps")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputWordStart, inputNameStart, inputExact))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "maps")

        assertEquals(listOf(inputExact, inputNameStart, inputWordStart), actualResults)
    }

    @Test
    fun `matches a word start after punctuation`() {
        val inputApp: Launchable = createRailLaunchable("F-Droid")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputApp))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "droid")

        assertEquals(listOf(inputApp), actualResults)
    }

    @Test
    fun `does not match a word start for a single character`() {
        val inputDroid: Launchable = createRailLaunchable("F-Droid")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputDroid))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "d")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `matches a single character against an exact name and a name start`() {
        val inputExact: Launchable = createRailLaunchable("D")
        val inputNameStart: Launchable = createRailLaunchable("Drive")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputNameStart, inputExact))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "d")

        assertEquals(listOf(inputExact, inputNameStart), actualResults)
    }

    @Test
    fun `matches digits as ordinary characters`() {
        val inputApp: Launchable = createRailLaunchable("99")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputApp))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "99")

        assertEquals(listOf(inputApp), actualResults)
    }

    @Test
    fun `breaks ties in App List order`() {
        val inputAlpine: Launchable = createRailLaunchable("Alpine")
        val inputAlpha: Launchable = createRailLaunchable("Alpha")
        val inputState: LaunchableState = createSearchState(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputAlpine, inputAlpha)))
        )

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "al")

        assertEquals(listOf(inputAlpine, inputAlpha), actualResults)
    }

    @Test
    fun `searches an App held in a Folder`() {
        val inputFolder: Launchable = Folder(folderId = 1L, name = "Games")
        val inputMember: App = createApp(name = "Tetris", packageName = "com.example.tetris")
        val inputState: LaunchableState = createSearchState(
            groups = listOf(LaunchableGroup.InitialGroup(label = "G", items = listOf(inputFolder))),
            extraLaunchables = listOf(inputMember)
        )

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "tetris")

        assertEquals(listOf(inputMember), actualResults)
    }

    @Test
    fun `shows a paused work App`() {
        val inputPausedApp: App = createPausedWorkApp()
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputPausedApp))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "gmail")

        assertEquals(listOf(inputPausedApp), actualResults)
    }

    @Test
    fun `leaves private Apps out while their Private Space is Locked`() {
        val inputPrivateApp: App = createPrivateApp()
        val inputState: LaunchableState = createSearchState(
            groups = listOf(
                LaunchableGroup.InitialGroup(label = "A", items = listOf(createRailLaunchable("Alpha"))),
                LaunchableGroup.PrivateSpace.Locked
            ),
            extraLaunchables = listOf(inputPrivateApp)
        )

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "secret")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `shows private Apps while their Private Space is Unlocked`() {
        val inputPrivateApp: App = createPrivateApp()
        val inputState: LaunchableState = createSearchState(
            groups = listOf(
                LaunchableGroup.InitialGroup(label = "A", items = listOf(createRailLaunchable("Alpha"))),
                LaunchableGroup.PrivateSpace.Unlocked(items = listOf(inputPrivateApp))
            )
        )

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "secret")

        assertEquals(listOf(inputPrivateApp), actualResults)
    }

    @Test
    fun `returns nothing for a blank query`() {
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(createRailLaunchable("Alpha")))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "  ")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `returns nothing for a query no Launchable matches`() {
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(createRailLaunchable("Alpha")))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "zulu")

        assertTrue(actualResults.isEmpty())
    }

    private fun createSearchState(groups: List<LaunchableGroup.Listed>, extraLaunchables: List<Launchable> = emptyList()): LaunchableState =
        createLaunchableState(
            groups = listOf(LaunchableGroup.Star) + groups + createBuiltinGroup(),
            folders = extraLaunchables
        )
}
