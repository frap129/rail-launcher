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
    private val inputRvnc: Launchable = createRailLaunchable("RVNC")
    private val inputRvncState: LaunchableState = createLoadedLaunchableState(listOf(inputRvnc))
    private val inputMaps: Launchable = createRailLaunchable("Maps")
    private val inputMapsState: LaunchableState = createLoadedLaunchableState(listOf(inputMaps))
    private val inputCalendar: Launchable = createRailLaunchable("Calendar")
    private val inputCalendarState: LaunchableState = createLoadedLaunchableState(listOf(inputCalendar))
    private val inputRenamedCalendar: App = createApp(name = "Calendar", packageName = "com.example.calendar", customName = "Planner")
    private val inputRenamedCalendarState: LaunchableState = createLoadedLaunchableState(listOf(inputRenamedCalendar))

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

    @Test
    fun `matches the initials of consecutive words`() {
        val inputGoogleMaps: Launchable = createRailLaunchable("Google Maps")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputGoogleMaps))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "gm")

        assertEquals(listOf(inputGoogleMaps), actualResults)
    }

    @Test
    fun `does not match initials taken from non-consecutive words`() {
        val inputGoogleMapsApp: Launchable = createRailLaunchable("Google Maps App")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputGoogleMapsApp))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "ga")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `does not match initials when the query is longer than the words`() {
        val inputGoogle: Launchable = createRailLaunchable("Google")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputGoogle))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "gm")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `does not find a renamed Folder by its placeholder default name`() {
        val inputFolder: Launchable = Folder(folderId = 1L, name = "Games")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputFolder))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "folder")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `matches inside a word from three characters`() {
        val actualResults: List<Launchable> = inputRvncState.searchLaunchables(query = "vnc")

        assertEquals(listOf(inputRvnc), actualResults)
    }

    @Test
    fun `does not match inside a word below three characters`() {
        val actualResults: List<Launchable> = inputRvncState.searchLaunchables(query = "vn")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `ranks exact, name start, word start, initials and in-word matches in order`() {
        val inputExact: Launchable = createRailLaunchable("Gma")
        val inputNameStart: Launchable = createRailLaunchable("Gmail")
        val inputWordStart: Launchable = createRailLaunchable("Big Gmat")
        val inputInitials: Launchable = createRailLaunchable("Google Maps App")
        val inputInWord: Launchable = createRailLaunchable("Sigma")
        val inputState: LaunchableState = createLoadedLaunchableState(
            listOf(inputInWord, inputInitials, inputWordStart, inputNameStart, inputExact)
        )

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "gma")

        val expectedResults: List<Launchable> = listOf(inputExact, inputNameStart, inputWordStart, inputInitials, inputInWord)
        assertEquals(expectedResults, actualResults)
    }

    @Test
    fun `matches one typo from five characters`() {
        val actualResults: List<Launchable> = inputMapsState.searchLaunchables(query = "mapzs")

        assertEquals(listOf(inputMaps), actualResults)
    }

    @Test
    fun `matches an adjacent transposition from five characters`() {
        val inputGmail: Launchable = createRailLaunchable("Gmail")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputGmail))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "gmial")

        assertEquals(listOf(inputGmail), actualResults)
    }

    @Test
    fun `does not match a typo below five characters`() {
        val actualResults: List<Launchable> = inputMapsState.searchLaunchables(query = "mapz")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `allows two typos from eight characters`() {
        val actualResults: List<Launchable> = inputCalendarState.searchLaunchables(query = "calenxxr")

        assertEquals(listOf(inputCalendar), actualResults)
    }

    @Test
    fun `does not allow two typos below eight characters`() {
        val actualResults: List<Launchable> = inputCalendarState.searchLaunchables(query = "calenxa")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `ranks an in-word match above a typo`() {
        val inputInWord: Launchable = createRailLaunchable("Hydroid")
        val inputTypo: Launchable = createRailLaunchable("Droie")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputTypo, inputInWord))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "droid")

        assertEquals(listOf(inputInWord, inputTypo), actualResults)
    }

    @Test
    fun `finds a renamed App by its default name`() {
        val actualResults: List<Launchable> = inputRenamedCalendarState.searchLaunchables(query = "calendar")

        assertEquals(listOf(inputRenamedCalendar), actualResults)
    }

    @Test
    fun `finds a renamed App by its custom name`() {
        val actualResults: List<Launchable> = inputRenamedCalendarState.searchLaunchables(query = "planner")

        assertEquals(listOf(inputRenamedCalendar), actualResults)
    }

    @Test
    fun `keeps the stronger match between a custom and a default name`() {
        val inputRenamed: App = createApp(name = "Maps", packageName = "com.example.maps", customName = "My Maps")
        val inputRival: Launchable = createRailLaunchable("Maps Pro")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputRival, inputRenamed))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "maps")

        assertEquals(listOf(inputRenamed, inputRival), actualResults)
    }

    @Test
    fun `does not use word starts, initials or in-word matching for a single character`() {
        val inputInitials: Launchable = createRailLaunchable("Google Maps")
        val inputInWord: Launchable = createRailLaunchable("Palm")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputInitials, inputInWord))

        val actualResults: List<Launchable> = inputState.searchLaunchables(query = "m")

        assertTrue(actualResults.isEmpty())
    }

    private fun createSearchState(groups: List<LaunchableGroup.Listed>, extraLaunchables: List<Launchable> = emptyList()): LaunchableState =
        createLaunchableState(
            groups = listOf(LaunchableGroup.Star) + groups + createBuiltinGroup(),
            folders = extraLaunchables
        )
}
