package core.data.search

import core.data.apps.model.App
import core.data.folders.Folder
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.shortcuts.ShortcutLaunchable
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createBuiltinGroup
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createPausedWorkApp
import core.data.testing.createPrivateApp
import core.data.testing.createRailLaunchable
import core.data.testing.createShortcut
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

        val actualResults: List<Launchable> = inputState.searchResults(query = "CAFE")

        assertEquals(listOf(inputCafe), actualResults)
    }

    @Test
    fun `ranks an exact name above a name start and a word start`() {
        val inputExact: Launchable = createRailLaunchable("Maps")
        val inputNameStart: Launchable = createRailLaunchable("Maps Pro")
        val inputWordStart: Launchable = createRailLaunchable("Google Maps")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputWordStart, inputNameStart, inputExact))

        val actualResults: List<Launchable> = inputState.searchResults(query = "maps")

        assertEquals(listOf(inputExact, inputNameStart, inputWordStart), actualResults)
    }

    @Test
    fun `matches a word start after punctuation`() {
        val inputApp: Launchable = createRailLaunchable("F-Droid")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputApp))

        val actualResults: List<Launchable> = inputState.searchResults(query = "droid")

        assertEquals(listOf(inputApp), actualResults)
    }

    @Test
    fun `does not match a word start for a single character`() {
        val inputDroid: Launchable = createRailLaunchable("F-Droid")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputDroid))

        val actualResults: List<Launchable> = inputState.searchResults(query = "d")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `matches a single character against an exact name and a name start`() {
        val inputExact: Launchable = createRailLaunchable("D")
        val inputNameStart: Launchable = createRailLaunchable("Drive")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputNameStart, inputExact))

        val actualResults: List<Launchable> = inputState.searchResults(query = "d")

        assertEquals(listOf(inputExact, inputNameStart), actualResults)
    }

    @Test
    fun `matches digits as ordinary characters`() {
        val inputApp: Launchable = createRailLaunchable("99")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputApp))

        val actualResults: List<Launchable> = inputState.searchResults(query = "99")

        assertEquals(listOf(inputApp), actualResults)
    }

    @Test
    fun `ranks a more used Launchable above an equally strong match`() {
        val inputAlpine: Launchable = createRailLaunchable("Alpine")
        val inputAlpha: Launchable = createRailLaunchable("Alpha")
        val inputState = createSearchState(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputAlpine, inputAlpha)))
        )

        val actualResults: List<Launchable> = inputState.searchResults(
            query = "al",
            launchCounts = mapOf(inputAlpine.key to 1, inputAlpha.key to 3)
        )

        assertEquals(listOf(inputAlpha, inputAlpine), actualResults)
    }

    @Test
    fun `does not let usage beat a stronger match tier`() {
        val inputExact: Launchable = createRailLaunchable("Maps")
        val inputNameStart: Launchable = createRailLaunchable("Maps Pro")
        val inputState = createSearchState(
            groups = listOf(LaunchableGroup.InitialGroup(label = "M", items = listOf(inputNameStart, inputExact)))
        )

        val actualResults: List<Launchable> = inputState.searchResults(
            query = "maps",
            launchCounts = mapOf(inputNameStart.key to 10)
        )

        assertEquals(listOf(inputExact, inputNameStart), actualResults)
    }

    @Test
    fun `breaks ties in App List order`() {
        val inputAlpine: Launchable = createRailLaunchable("Alpine")
        val inputAlpha: Launchable = createRailLaunchable("Alpha")
        val inputState: LaunchableState = createSearchState(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputAlpine, inputAlpha)))
        )

        val actualResults: List<Launchable> = inputState.searchResults(query = "al")

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

        val actualResults: List<Launchable> = inputState.searchResults(query = "tetris")

        assertEquals(listOf(inputMember), actualResults)
    }

    @Test
    fun `shows a paused work App`() {
        val inputPausedApp: App = createPausedWorkApp()
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputPausedApp))

        val actualResults: List<Launchable> = inputState.searchResults(query = "gmail")

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

        val actualResults: List<Launchable> = inputState.searchResults(query = "secret")

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

        val actualResults: List<Launchable> = inputState.searchResults(query = "secret")

        assertEquals(listOf(inputPrivateApp), actualResults)
    }

    @Test
    fun `returns nothing for a blank query`() {
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(createRailLaunchable("Alpha")))

        val actualResults: List<Launchable> = inputState.searchResults(query = "  ")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `returns nothing for a query no Launchable matches`() {
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(createRailLaunchable("Alpha")))

        val actualResults: List<Launchable> = inputState.searchResults(query = "zulu")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `matches the initials of consecutive words`() {
        val inputGoogleMaps: Launchable = createRailLaunchable("Google Maps")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputGoogleMaps))

        val actualResults: List<Launchable> = inputState.searchResults(query = "gm")

        assertEquals(listOf(inputGoogleMaps), actualResults)
    }

    @Test
    fun `does not match initials taken from non-consecutive words`() {
        val inputGoogleMapsApp: Launchable = createRailLaunchable("Google Maps App")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputGoogleMapsApp))

        val actualResults: List<Launchable> = inputState.searchResults(query = "ga")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `does not match initials when the query is longer than the words`() {
        val inputGoogle: Launchable = createRailLaunchable("Google")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputGoogle))

        val actualResults: List<Launchable> = inputState.searchResults(query = "gm")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `does not find a renamed Folder by its placeholder default name`() {
        val inputFolder: Launchable = Folder(folderId = 1L, name = "Games")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputFolder))

        val actualResults: List<Launchable> = inputState.searchResults(query = "folder")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `matches inside a word from three characters`() {
        val actualResults: List<Launchable> = inputRvncState.searchResults(query = "vnc")

        assertEquals(listOf(inputRvnc), actualResults)
    }

    @Test
    fun `does not match inside a word below three characters`() {
        val actualResults: List<Launchable> = inputRvncState.searchResults(query = "vn")

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

        val actualResults: List<Launchable> = inputState.searchResults(query = "gma")

        val expectedResults: List<Launchable> = listOf(inputExact, inputNameStart, inputWordStart, inputInitials, inputInWord)
        assertEquals(expectedResults, actualResults)
    }

    @Test
    fun `matches one typo from five characters`() {
        val actualResults: List<Launchable> = inputMapsState.searchResults(query = "mapzs")

        assertEquals(listOf(inputMaps), actualResults)
    }

    @Test
    fun `matches an adjacent transposition from five characters`() {
        val inputGmail: Launchable = createRailLaunchable("Gmail")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputGmail))

        val actualResults: List<Launchable> = inputState.searchResults(query = "gmial")

        assertEquals(listOf(inputGmail), actualResults)
    }

    @Test
    fun `does not match a typo below five characters`() {
        val actualResults: List<Launchable> = inputMapsState.searchResults(query = "mapz")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `allows two typos from eight characters`() {
        val actualResults: List<Launchable> = inputCalendarState.searchResults(query = "calenxxr")

        assertEquals(listOf(inputCalendar), actualResults)
    }

    @Test
    fun `does not allow two typos below eight characters`() {
        val actualResults: List<Launchable> = inputCalendarState.searchResults(query = "calenxa")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `ranks an in-word match above a typo`() {
        val inputInWord: Launchable = createRailLaunchable("Hydroid")
        val inputTypo: Launchable = createRailLaunchable("Droie")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputTypo, inputInWord))

        val actualResults: List<Launchable> = inputState.searchResults(query = "droid")

        assertEquals(listOf(inputInWord, inputTypo), actualResults)
    }

    @Test
    fun `finds a renamed App by its default name`() {
        val actualResults: List<Launchable> = inputRenamedCalendarState.searchResults(query = "calendar")

        assertEquals(listOf(inputRenamedCalendar), actualResults)
    }

    @Test
    fun `finds a renamed App by its custom name`() {
        val actualResults: List<Launchable> = inputRenamedCalendarState.searchResults(query = "planner")

        assertEquals(listOf(inputRenamedCalendar), actualResults)
    }

    @Test
    fun `keeps the stronger match between a custom and a default name`() {
        val inputRenamed: App = createApp(name = "Maps", packageName = "com.example.maps", customName = "My Maps")
        val inputRival: Launchable = createRailLaunchable("Maps Pro")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputRival, inputRenamed))

        val actualResults: List<Launchable> = inputState.searchResults(query = "maps")

        assertEquals(listOf(inputRenamed, inputRival), actualResults)
    }

    @Test
    fun `does not use word starts, initials or in-word matching for a single character`() {
        val inputInitials: Launchable = createRailLaunchable("Google Maps")
        val inputInWord: Launchable = createRailLaunchable("Palm")
        val inputState: LaunchableState = createLoadedLaunchableState(listOf(inputInitials, inputInWord))

        val actualResults: List<Launchable> = inputState.searchResults(query = "m")

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `matches a Shortcut from two characters`() {
        val inputShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.messages", longLabel = "New message")

        val actualResults: List<Launchable> = searchWithShortcuts(query = "ne", shortcuts = listOf(inputShortcut))

        assertEquals(listOf(inputShortcut.copy(publisherName = "Messages")), actualResults)
    }

    @Test
    fun `does not match a Shortcut for a single character`() {
        val inputShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.messages", longLabel = "New message")

        val actualResults: List<Launchable> = searchWithShortcuts(query = "n", shortcuts = listOf(inputShortcut))

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `leaves out a Hidden Shortcut`() {
        val inputShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.messages", longLabel = "New message")

        val actualResults: List<Launchable> = searchWithShortcuts(
            query = "new",
            shortcuts = listOf(inputShortcut),
            hiddenShortcutIds = setOf(inputShortcut.shortcutId)
        )

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `leaves out a disabled Shortcut`() {
        val inputShortcut: ShortcutLaunchable = createShortcut(
            packageName = "com.example.messages",
            longLabel = "New message",
            isEnabled = false
        )

        val actualResults: List<Launchable> = searchWithShortcuts(query = "new", shortcuts = listOf(inputShortcut))

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `ranks a Shortcut below a Launchable of the same tier`() {
        val inputLaunchable: Launchable = createRailLaunchable("Maps")
        val inputShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.maps", longLabel = "Maps")

        val actualResults: List<Launchable> = searchWithShortcuts(
            query = "maps",
            shortcuts = listOf(inputShortcut),
            publisherName = "Maps App",
            launchables = listOf(inputLaunchable),
            launchCounts = mapOf(inputShortcut.key to 10)
        )

        val expectedResults: List<Launchable> = listOf(inputLaunchable, inputShortcut.copy(publisherName = "Maps App"))
        assertEquals(expectedResults, actualResults)
    }

    @Test
    fun `matches a paused Shortcut`() {
        val inputPausedPublisher: App = createPausedWorkApp()
        val inputShortcut: ShortcutLaunchable = createShortcut(
            packageName = inputPausedPublisher.packageName,
            profile = inputPausedPublisher.profile,
            longLabel = "Compose",
            profileInfo = inputPausedPublisher.profileInfo
        )

        val actualResults: List<Launchable> = searchWithShortcuts(
            query = "compose",
            shortcuts = listOf(inputShortcut),
            publisherName = inputPausedPublisher.name
        )

        assertEquals(listOf(inputShortcut.copy(publisherName = inputPausedPublisher.name)), actualResults)
        assertTrue(actualResults.single().isPaused)
    }

    @Test
    fun `leaves a private Shortcut out while its Private Space is Locked`() {
        val inputPrivatePublisher: App = createPrivateApp()
        val inputShortcut: ShortcutLaunchable = createShortcut(
            packageName = inputPrivatePublisher.packageName,
            profile = inputPrivatePublisher.profile,
            longLabel = "Secret action",
            profileInfo = inputPrivatePublisher.profileInfo
        )
        val inputState: LaunchableState = createSearchState(groups = listOf(LaunchableGroup.PrivateSpace.Locked))

        val actualResults: List<Launchable> = inputState.searchResults(
            query = "secret",
            shortcuts = listOf(
                SearchShortcut(shortcut = inputShortcut, publisherName = inputPrivatePublisher.name, isHidden = false)
            )
        )

        assertTrue(actualResults.isEmpty())
    }

    @Test
    fun `lists a Shortcut published as a Standalone shortcut only once`() {
        val inputShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.messages", longLabel = "New message")

        val actualResults: List<Launchable> = searchWithShortcuts(
            query = "new",
            shortcuts = listOf(inputShortcut),
            launchables = listOf(inputShortcut)
        )

        assertEquals(listOf(inputShortcut), actualResults)
    }

    private fun searchWithShortcuts(
        query: String,
        shortcuts: List<ShortcutLaunchable>,
        publisherName: String = "Messages",
        hiddenShortcutIds: Set<String> = emptySet(),
        launchables: List<Launchable> = listOf(createRailLaunchable("Alpha")),
        launchCounts: Map<String, Int> = emptyMap()
    ): List<Launchable> = createLoadedLaunchableState(launchables = launchables).searchResults(
        query = query,
        launchCounts = launchCounts,
        shortcuts = shortcuts.map { shortcut ->
            SearchShortcut(
                shortcut = shortcut,
                publisherName = publisherName,
                isHidden = shortcut.shortcutId in hiddenShortcutIds
            )
        }
    )

    private fun createSearchState(groups: List<LaunchableGroup.Listed>, extraLaunchables: List<Launchable> = emptyList()): LaunchableState =
        createLaunchableState(
            groups = listOf(LaunchableGroup.Star) + groups + createBuiltinGroup(),
            folders = extraLaunchables
        )
}
