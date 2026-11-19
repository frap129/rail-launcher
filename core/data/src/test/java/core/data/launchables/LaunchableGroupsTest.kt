package core.data.launchables

import core.data.apps.model.App
import core.data.folders.Folder
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.launchables.model.RecentlyInstalledSection
import core.data.launches.LaunchCountRecord
import core.data.profiles.PrivateSpaceStatus
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.testing.LocaleGroupingCase
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_FOURTEEN_DAYS_MS
import core.data.testing.TEST_NOW_EPOCH_MS
import core.data.testing.TEST_RAIL_ICON_RES_ID
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createBuiltinGroup
import core.data.testing.createFixedPrivateSpaceStatus
import core.data.testing.createLaunchCountRecord
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createPrivateApp
import core.data.testing.createProfileInfo
import core.data.testing.createShortcut
import core.data.testing.localeGroupingCases
import core.util.MILLIS_PER_DAY
import core.util.MILLIS_PER_HOUR
import core.util.MILLIS_PER_MINUTE
import core.util.RECENT_INSTALL_WINDOW_MS
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class LaunchableGroupsTest {
    private val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
    private val inputBuiltinGroup: LaunchableGroup.Builtin = createBuiltinGroup()

    @Test
    fun `places the Star above A`() {
        val inputItems: List<Launchable> = listOf(createLaunchable("Beta"), createLaunchable("Alpha"))
        val actualLabels: List<String> = inputItems.toLaunchableGroups(inputBuiltinGroup, locale = Locale.US).map { it.label }
        assertEquals(listOf("★", "A", "B", "⌂"), actualLabels)
    }

    @Test
    fun `places the Star above #`() {
        val inputItems: List<Launchable> = listOf(createLaunchable("Alpha"), createLaunchable("1Password"))
        val actualLabels: List<String> = inputItems.toLaunchableGroups(inputBuiltinGroup, locale = Locale.US).map { it.label }
        assertEquals(listOf("★", "#", "A", "⌂"), actualLabels)
    }

    @Test
    fun `keeps at most the cache size of Group keys per locale`() {
        val inputLocale: Locale = Locale.forLanguageTag("en-x-gcache")
        val inputNames: List<String> = (0 until GROUP_KEY_CACHE_SIZE + 10).map { index -> "App $index" }

        prewarmGroupKeys(names = inputNames, locale = inputLocale)

        assertEquals(GROUP_KEY_CACHE_SIZE, cachedGroupKeyCount(locale = inputLocale))
    }

    @Test
    fun `prewarmed Group keys match the keys a cold grouping forms`() {
        val inputItems: List<Launchable> = listOf("Яндекс", "東京", "Zebra").map { name -> createLaunchable(name) }
        val inputWarmLocale: Locale = Locale.forLanguageTag("en-x-gwarm")
        prewarmGroupKeys(names = inputItems.map { item -> item.name }, locale = inputWarmLocale)

        val expectedGroups: List<LaunchableGroup> = inputItems.toLaunchableGroups(
            inputBuiltinGroup,
            locale = Locale.forLanguageTag("en-x-gcold")
        )
        val actualGroups: List<LaunchableGroup> = inputItems.toLaunchableGroups(inputBuiltinGroup, locale = inputWarmLocale)

        assertEquals(expectedGroups, actualGroups)
    }

    @Test
    fun `keeps # before groups whose first character sorts below it`() {
        val inputItems: List<Launchable> = listOf(
            createLaunchable("!Bang"),
            createLaunchable("\"Quoted"),
            createLaunchable("Alpha"),
            createLaunchable("1Password")
        )

        val actualLabels: List<String> = inputItems.toLaunchableGroups(inputBuiltinGroup, locale = Locale.US).map { it.label }

        assertEquals("#", actualLabels[1])
        assertEquals(setOf("!", "\"", "A"), actualLabels.drop(2).dropLast(1).toSet())
    }

    @Test
    fun `groups names starting with any digit under #`() {
        val inputZero: Launchable = createLaunchable("0xBench")
        val inputOne: Launchable = createLaunchable("1Password")
        val inputNine: Launchable = createLaunchable("9GAG")
        val actualGroup: LaunchableGroup = listOf(inputZero, inputOne, inputNine)
            .toLaunchableGroups(inputBuiltinGroup, locale = Locale.US)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "#", items = listOf(inputZero, inputOne, inputNine)), actualGroup)
    }

    @Test
    fun `groups names starting with a non-ASCII digit under #`() {
        val inputItem: Launchable = createLaunchable("１Password")
        val actualGroup: LaunchableGroup = listOf(inputItem).toLaunchableGroups(inputBuiltinGroup, locale = Locale.US)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "#", items = listOf(inputItem)), actualGroup)
    }

    @Test
    fun `groups names with no initial character under #`() {
        val inputItem: Launchable = createLaunchable("")
        val actualGroup: LaunchableGroup = listOf(inputItem).toLaunchableGroups(inputBuiltinGroup, locale = Locale.US)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "#", items = listOf(inputItem)), actualGroup)
    }

    @Test
    fun `places a Folder in the alphabetical group for its resolved name`() {
        val inputFolder: Folder = Folder(folderId = 1L, name = "Zulu")
        val inputItems: List<Launchable> = listOf(createLaunchable("Alpha"), inputFolder)
        val actualGroups: List<LaunchableGroup> = inputItems.toLaunchableGroups(inputBuiltinGroup, locale = Locale.US)

        assertEquals(listOf("★", "A", "Z", "⌂"), actualGroups.map { group -> group.label })
        assertEquals(
            listOf(inputFolder),
            actualGroups.filterIsInstance<LaunchableGroup.InitialGroup>().single { group ->
                group.label == "Z"
            }.items
        )
    }

    @Test
    fun `groups a Folder with a digit name under #`() {
        val inputFolder: Folder = Folder(folderId = 1L, name = "1Games")
        val actualGroup: LaunchableGroup = listOf(inputFolder).toLaunchableGroups(inputBuiltinGroup, locale = Locale.US)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "#", items = listOf(inputFolder)), actualGroup)
    }

    @Test
    fun `groups a Folder with no initial character under #`() {
        val inputFolder: Folder = Folder(folderId = 1L, name = "")
        val actualGroup: LaunchableGroup = listOf(inputFolder).toLaunchableGroups(inputBuiltinGroup, locale = Locale.US)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "#", items = listOf(inputFolder)), actualGroup)
    }

    @Test
    fun `groups Launchables by first character`() {
        val inputAlpha: Launchable = createLaunchable("Alpha")
        val inputAnchor: Launchable = createLaunchable("anchor")
        val actualGroup: LaunchableGroup = listOf(inputAlpha, inputAnchor).toLaunchableGroups(inputBuiltinGroup, locale = Locale.US)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputAlpha, inputAnchor)), actualGroup)
    }

    @Test
    fun `keys and orders the Initial groups as each locale defines them`() {
        localeGroupingCases.forEach(::assertInitialGroupLabels)
    }

    @Test
    fun `merges a variant letter into the locale's own letter`() {
        val inputAeble: Launchable = createLaunchable("Æble")
        val inputAebleUmlaut: Launchable = createLaunchable("Äble")

        val actualGroup: LaunchableGroup = listOf(inputAebleUmlaut, inputAeble).toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            locale = Locale.forLanguageTag("da")
        )[1]

        assertEquals(
            LaunchableGroup.InitialGroup(label = "Æ", items = listOf(inputAebleUmlaut, inputAeble)),
            actualGroup
        )
    }

    @Test
    fun `puts a folded accent in its base letter's Initial group`() {
        val inputEcole: Launchable = createLaunchable("École")

        val actualGroup: LaunchableGroup = listOf(inputEcole).toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            locale = Locale.FRENCH
        )[1]

        assertEquals(LaunchableGroup.InitialGroup(label = "E", items = listOf(inputEcole)), actualGroup)
    }

    @Test
    fun `keeps the Builtin group last`() {
        val inputItems: List<Launchable> = listOf(createLaunchable("Zulu"))
        val actualGroup: LaunchableGroup = inputItems.toLaunchableGroups(inputBuiltinGroup, locale = Locale.US).last()
        assertEquals(inputBuiltinGroup, actualGroup)
    }

    @Test
    fun `keeps the Star and Builtin groups when there are no Launchables`() {
        val actualGroups: List<LaunchableGroup> = emptyList<Launchable>().toLaunchableGroups(inputBuiltinGroup, locale = Locale.US)
        assertEquals(listOf(LaunchableGroup.Star, inputBuiltinGroup), actualGroups)
    }

    @Test
    fun `places the Private Space group after the Builtin group`() {
        val inputItems: List<Launchable> = listOf(createLaunchable("Alpha"))

        val actualLabels: List<String> = inputItems.toLaunchableGroups(
            locale = Locale.US,
            builtinGroup = inputBuiltinGroup,
            privateSpaceStatus = createFixedPrivateSpaceStatus(profile = inputPrivateProfile, hasListedApps = true)
        ).map { group -> group.label }

        assertEquals(listOf("★", "A", "⌂", LaunchableGroup.PrivateSpace.LABEL), actualLabels)
    }

    @Test
    fun `gives a Locked Private Space no items`() {
        val inputPrivateApp: Launchable = createPrivateApp(name = "Secret")

        val actualGroup: LaunchableGroup.PrivateSpace = listOf(inputPrivateApp).toLaunchableGroups(
            locale = Locale.US,
            builtinGroup = inputBuiltinGroup,
            privateSpaceStatus = createFixedPrivateSpaceStatus(profile = inputPrivateProfile, isLocked = true)
        ).filterIsInstance<LaunchableGroup.PrivateSpace>().single()

        assertTrue(actualGroup.isLocked)
        assertTrue(actualGroup.items.isEmpty())
    }

    @Test
    fun `leaves a Hidden Private Space off the groups`() {
        val actualGroups: List<LaunchableGroup> = emptyList<Launchable>().toLaunchableGroups(
            locale = Locale.US,
            builtinGroup = inputBuiltinGroup,
            privateSpaceStatus = createFixedPrivateSpaceStatus(profile = inputPrivateProfile, isHidden = true, isLocked = true)
        )

        assertEquals(listOf("★", "⌂"), actualGroups.map { group -> group.label })
        assertTrue(actualGroups.none { group -> group is LaunchableGroup.PrivateSpace })
    }

    @Test
    fun `lists private Apps A-Z in an Unlocked Private Space`() {
        val inputZulu: Launchable = createPrivateApp(name = "Zulu", packageName = "com.example.zulu")
        val inputAlpha: Launchable = createPrivateApp(name = "alpha", packageName = "com.example.alpha")

        val actualGroup: LaunchableGroup.PrivateSpace = listOf(inputZulu, inputAlpha).toAppListGroups(
            ranking = AppListRanking.Alphabetical,
            privateSpaceStatus = createFixedPrivateSpaceStatus(profile = inputPrivateProfile, hasListedApps = true)
        ).filterIsInstance<LaunchableGroup.PrivateSpace>().single()

        assertFalse(actualGroup.isLocked)
        assertEquals(listOf(inputAlpha, inputZulu), actualGroup.items)
    }

    @Test
    fun `leaves the Private Space group off without a Private Space`() {
        val inputPrivateApp: Launchable = createPrivateApp(name = "Secret")

        val actualGroups: List<LaunchableGroup> = listOf(inputPrivateApp)
            .toLaunchableGroups(builtinGroup = inputBuiltinGroup, locale = Locale.US)

        assertEquals(listOf("★", "⌂"), actualGroups.map { group -> group.label })
        assertTrue(actualGroups.none { group -> group is LaunchableGroup.PrivateSpace })
    }

    @Test
    fun `keeps private Apps out of the Initial groups`() {
        val inputPrivateApp: Launchable = createPrivateApp(name = "Secret")

        val actualGroups: List<LaunchableGroup> = listOf(inputPrivateApp, createLaunchable("Alpha")).toLaunchableGroups(
            locale = Locale.US,
            builtinGroup = inputBuiltinGroup,
            privateSpaceStatus = createFixedPrivateSpaceStatus(profile = inputPrivateProfile, hasListedApps = true)
        )
        val actualInitialItems: List<Launchable> = actualGroups.filterIsInstance<LaunchableGroup.InitialGroup>()
            .flatMap { group -> group.items }

        assertFalse(actualInitialItems.contains(inputPrivateApp))
    }

    @Test
    fun `puts a Private Space Shortcut in the Private Space group`() {
        val inputPrivateShortcut: Launchable = createShortcut(
            packageName = "com.example.private",
            profile = inputPrivateProfile.user,
            longLabel = "Secret shortcut"
        )

        val actualGroups: List<LaunchableGroup> = listOf(inputPrivateShortcut).toLaunchableGroups(
            locale = Locale.US,
            builtinGroup = inputBuiltinGroup,
            privateSpaceStatus = createFixedPrivateSpaceStatus(profile = inputPrivateProfile, hasListedApps = true)
        )
        val actualGroup: LaunchableGroup.PrivateSpace = actualGroups.filterIsInstance<LaunchableGroup.PrivateSpace>().single()

        assertFalse(actualGroup.isLocked)
        assertEquals(listOf(inputPrivateShortcut), actualGroup.items)
        assertFalse(
            actualGroups.filterIsInstance<LaunchableGroup.InitialGroup>().any { group ->
                group.items.contains(inputPrivateShortcut)
            }
        )
    }

    @Test
    fun `gives the Private Space group a label apart from the other Rail glyphs and initials`() {
        val actualLabel: String = LaunchableGroup.PrivateSpace.LABEL
        val inputOtherLabels: List<String> = listOf(LaunchableGroup.Star.label, inputBuiltinGroup.label)

        assertFalse(actualLabel in inputOtherLabels)
        assertFalse(actualLabel.first().isLetterOrDigit())
    }

    @Test
    fun `Alphabetical keeps the order the items arrive in`() {
        val inputZeta: Launchable = createLaunchable("Zeta")
        val inputZulu: Launchable = createLaunchable("Zulu")
        val inputZebra: Launchable = createLaunchable("Zebra")

        val actualGroup: LaunchableGroup = listOf(inputZeta, inputZulu, inputZebra).toAppListGroups(
            ranking = AppListRanking.Alphabetical
        )[1]

        assertEquals(
            LaunchableGroup.InitialGroup(label = "Z", items = listOf(inputZeta, inputZulu, inputZebra)),
            actualGroup
        )
    }

    @Test
    fun `Most used orders by Launch count with case-insensitive A-Z ties and never-launched items last`() {
        val inputAlpine: Launchable = createLaunchable("Alpine")
        val inputAlfred: Launchable = createLaunchable("alfred")
        val inputAmber: Launchable = createLaunchable("Amber")
        val inputAzure: Launchable = createLaunchable("azure")
        val inputCounts: Map<String, LaunchCountRecord> = mapOf(
            inputAlpine.key to createLaunchCountRecord(key = inputAlpine.key, launchCount = 3),
            inputAlfred.key to createLaunchCountRecord(key = inputAlfred.key, launchCount = 3)
        )

        val actualGroup: LaunchableGroup = listOf(inputAzure, inputAmber, inputAlpine, inputAlfred).toAppListGroups(
            ranking = AppListRanking.MostUsed(usage = createUsage(launchCountsByKey = inputCounts, rankFoldersByUse = false))
        )[1]

        assertEquals(
            LaunchableGroup.InitialGroup(
                label = "A",
                items = listOf(inputAlfred, inputAlpine, inputAmber, inputAzure)
            ),
            actualGroup
        )
    }

    @Test
    fun `Recently used ranks a recent launch above an older larger total`() {
        val inputOlder: Launchable = createLaunchable("Once")
        val inputRecent: Launchable = createLaunchable("Only")

        val actualItems: List<Launchable> = recentlyUsedItems(
            inputOlder = inputOlder,
            inputOlderScore = 10.0,
            inputOlderAgeMs = TEST_FOURTEEN_DAYS_MS,
            inputRecent = inputRecent,
            inputRecentScore = 3.0
        )

        assertEquals(listOf(inputRecent, inputOlder), actualItems)
    }

    @Test
    fun `Recently used keeps a day-old larger total above a newer smaller score`() {
        val inputOlder: Launchable = createLaunchable("Once")
        val inputRecent: Launchable = createLaunchable("Only")

        val actualItems: List<Launchable> = recentlyUsedItems(
            inputOlder = inputOlder,
            inputOlderScore = 10.0,
            inputOlderAgeMs = MILLIS_PER_DAY,
            inputRecent = inputRecent,
            inputRecentScore = 1.0
        )

        assertEquals(listOf(inputOlder, inputRecent), actualItems)
    }

    @Test
    fun `Recently used keeps never-launched items last and A-Z`() {
        val inputZulu: Launchable = createLaunchable("Zulu")
        val inputZebra: Launchable = createLaunchable("Zebra")
        val inputZanzibar: Launchable = createLaunchable("zanzibar")
        val inputCounts: Map<String, LaunchCountRecord> = mapOf(
            inputZulu.key to createLaunchCountRecord(key = inputZulu.key, launchCount = 1, recentUseScore = 1.0)
        )

        val actualGroup: LaunchableGroup = listOf(inputZebra, inputZanzibar, inputZulu).toAppListGroups(
            ranking = AppListRanking.RecentlyUsed(
                usage = createUsage(launchCountsByKey = inputCounts, rankFoldersByUse = false)
            )
        )[1]

        assertEquals(
            LaunchableGroup.InitialGroup(label = "Z", items = listOf(inputZulu, inputZanzibar, inputZebra)),
            actualGroup
        )
    }

    @Test
    fun `Most used keeps Folders last when Rank Folders by use is off`() {
        assertFoldersLast { usage -> AppListRanking.MostUsed(usage = usage) }
    }

    @Test
    fun `Recently used keeps Folders last when Rank Folders by use is off`() {
        assertFoldersLast { usage -> AppListRanking.RecentlyUsed(usage = usage) }
    }

    @Test
    fun `Most used ranks Folders by their Launch count when Rank Folders by use is on`() {
        val inputFixture: FolderRankingFixture = createFolderRankingFixture()

        val actualGroup: LaunchableGroup = inputFixture.items.toAppListGroups(
            ranking = AppListRanking.MostUsed(
                usage = inputFixture.createUsage(
                    firstFolderCount = 3,
                    secondFolderCount = 4,
                    rankFoldersByUse = true
                )
            )
        )[1]

        assertEquals(
            LaunchableGroup.InitialGroup(
                label = "B",
                items = listOf(inputFixture.secondFolder, inputFixture.firstFolder, inputFixture.otherApp, inputFixture.app)
            ),
            actualGroup
        )
    }

    @Test
    fun `Recently used ranks a Folder by its Recent use score when Rank Folders by use is on`() {
        val inputApp: Launchable = createLaunchable("Beta")
        val inputFolder: Folder = Folder(folderId = 1L, name = "Boulder folder")
        val inputCounts: Map<String, LaunchCountRecord> = mapOf(
            inputApp.key to createLaunchCountRecord(
                key = inputApp.key,
                launchCount = 10,
                recentUseScore = 10.0,
                scoreUpdatedAt = TEST_NOW_EPOCH_MS - TEST_FOURTEEN_DAYS_MS
            ),
            inputFolder.key to createLaunchCountRecord(
                key = inputFolder.key,
                launchCount = 1,
                recentUseScore = 3.0,
                scoreUpdatedAt = TEST_NOW_EPOCH_MS
            )
        )

        val actualGroup: LaunchableGroup = listOf(inputApp, inputFolder).toAppListGroups(
            ranking = AppListRanking.RecentlyUsed(
                usage = createUsage(launchCountsByKey = inputCounts, rankFoldersByUse = true)
            )
        )[1]

        assertEquals(LaunchableGroup.InitialGroup(label = "B", items = listOf(inputFolder, inputApp)), actualGroup)
    }

    @Test
    fun `Most used ranks the Unlocked Private Space group`() {
        val inputZulu: Launchable = createPrivateApp(name = "Zulu", packageName = "com.example.zulu")
        val inputAlpha: Launchable = createPrivateApp(name = "alpha", packageName = "com.example.alpha")
        val inputCounts: Map<String, LaunchCountRecord> = mapOf(
            inputZulu.key to createLaunchCountRecord(key = inputZulu.key, launchCount = 5),
            inputAlpha.key to createLaunchCountRecord(key = inputAlpha.key, launchCount = 1)
        )

        val actualGroup: LaunchableGroup.PrivateSpace = listOf(inputAlpha, inputZulu).toAppListGroups(
            ranking = AppListRanking.MostUsed(usage = createUsage(launchCountsByKey = inputCounts, rankFoldersByUse = false)),
            privateSpaceStatus = createFixedPrivateSpaceStatus(profile = inputPrivateProfile, hasListedApps = true)
        ).filterIsInstance<LaunchableGroup.PrivateSpace>().single()

        assertEquals(listOf(inputZulu, inputAlpha), actualGroup.items)
    }

    @Test
    fun `keeps the group order and the Builtin group while ranking by use`() {
        val inputZulu: Launchable = createLaunchable("Zulu")
        val inputAlpha: Launchable = createLaunchable("Alpha")
        val inputCounts: Map<String, LaunchCountRecord> = mapOf(
            inputAlpha.key to createLaunchCountRecord(key = inputAlpha.key, launchCount = 3)
        )

        val actualGroups: List<LaunchableGroup> = listOf(inputZulu, inputAlpha).toAppListGroups(
            ranking = AppListRanking.MostUsed(usage = createUsage(launchCountsByKey = inputCounts, rankFoldersByUse = false))
        )

        assertEquals(listOf("★", "A", "Z", "⌂"), actualGroups.map { group -> group.label })
        assertEquals(inputBuiltinGroup, actualGroups.last())
    }

    @Test
    fun `lists the section's Apps newest first`() {
        val inputOldest: App = createApp(
            name = "Alpha",
            packageName = "com.example.alpha",
            firstInstallTime = TEST_NOW_EPOCH_MS - RECENT_INSTALL_WINDOW_MS
        )
        val inputMiddle: App = createApp(
            name = "Beta",
            packageName = "com.example.beta",
            firstInstallTime = TEST_NOW_EPOCH_MS - MILLIS_PER_HOUR
        )
        val inputNewest: App = createApp(
            name = "Gamma",
            packageName = "com.example.gamma",
            firstInstallTime = TEST_NOW_EPOCH_MS - MILLIS_PER_MINUTE
        )
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputOldest, inputNewest, inputMiddle))

        val actualSection: RecentlyInstalledSection =
            requireNotNull(inputState.recentlyInstalledSection(nowEpochMs = TEST_NOW_EPOCH_MS))

        assertEquals(listOf(inputNewest, inputMiddle, inputOldest), actualSection.apps)
    }

    @Test
    fun `includes an App first installed exactly 3 days ago and leaves out an older one`() {
        val inputBoundary: App = createApp(
            name = "Boundary",
            packageName = "com.example.boundary",
            firstInstallTime = TEST_NOW_EPOCH_MS - RECENT_INSTALL_WINDOW_MS
        )
        val inputOlder: App = createApp(
            name = "Older",
            packageName = "com.example.older",
            firstInstallTime = TEST_NOW_EPOCH_MS - RECENT_INSTALL_WINDOW_MS - 1
        )
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputOlder, inputBoundary))

        val actualSection: RecentlyInstalledSection =
            requireNotNull(inputState.recentlyInstalledSection(nowEpochMs = TEST_NOW_EPOCH_MS))

        assertEquals(listOf(inputBoundary), actualSection.apps)
    }

    @Test
    fun `holds no section when no App was first installed within 3 days`() {
        val inputOldApp: App = createApp(
            name = "Alpha",
            packageName = "com.example.alpha",
            firstInstallTime = TEST_NOW_EPOCH_MS - RECENT_INSTALL_WINDOW_MS - 1
        )
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputOldApp))

        assertNull(inputState.recentlyInstalledSection(nowEpochMs = TEST_NOW_EPOCH_MS))
    }

    @Test
    fun `keeps a section App in its Initial group`() {
        val inputRecentApp: App = createApp(name = "Alpha", packageName = "com.example.alpha", firstInstallTime = TEST_NOW_EPOCH_MS)
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputRecentApp))

        val actualSection: RecentlyInstalledSection =
            requireNotNull(inputState.recentlyInstalledSection(nowEpochMs = TEST_NOW_EPOCH_MS))

        assertEquals(listOf(inputRecentApp), inputState.listedGroups.single().items)
        assertEquals(listOf(inputRecentApp), actualSection.apps)
    }

    @Test
    fun `leaves a Hidden App out of the section`() {
        val inputHiddenApp: App = createApp(name = "Alpha", packageName = "com.example.alpha", firstInstallTime = TEST_NOW_EPOCH_MS)
        val inputState: LaunchableState =
            createLoadedLaunchableState(launchables = listOf(inputHiddenApp), hiddenKeys = setOf(inputHiddenApp.key))

        assertNull(inputState.recentlyInstalledSection(nowEpochMs = TEST_NOW_EPOCH_MS))
    }

    @Test
    fun `leaves a Folder out of the section`() {
        val inputFolder: Folder = Folder(folderId = 1L, name = "Games")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputFolder), folders = listOf(inputFolder))

        assertNull(inputState.recentlyInstalledSection(nowEpochMs = TEST_NOW_EPOCH_MS))
    }

    @Test
    fun `includes a recent private App from an Unlocked Private Space group`() {
        val inputPrivateApp: App =
            createPrivateApp(name = "Secret", packageName = "com.example.secret", firstInstallTime = TEST_NOW_EPOCH_MS)
        val inputState: LaunchableState = createLaunchableState(
            groups = listOf(
                LaunchableGroup.Star,
                inputBuiltinGroup,
                LaunchableGroup.PrivateSpace.Unlocked(items = listOf(inputPrivateApp))
            )
        )

        val actualSection: RecentlyInstalledSection =
            requireNotNull(inputState.recentlyInstalledSection(nowEpochMs = TEST_NOW_EPOCH_MS))

        assertEquals(listOf(inputPrivateApp), actualSection.apps)
    }

    private fun assertFoldersLast(ranking: (AppListUsage) -> AppListRanking) {
        val inputFixture: FolderRankingFixture = createFolderRankingFixture()

        val actualGroup: LaunchableGroup = inputFixture.items.toAppListGroups(
            ranking = ranking(
                inputFixture.createUsage(
                    firstFolderCount = 99,
                    secondFolderCount = 98,
                    rankFoldersByUse = false
                )
            )
        )[1]

        assertEquals(
            LaunchableGroup.InitialGroup(
                label = "B",
                items = listOf(inputFixture.otherApp, inputFixture.app, inputFixture.firstFolder, inputFixture.secondFolder)
            ),
            actualGroup
        )
    }

    /**
     * The Recently used order of [inputOlder], scored [inputOlderScore] [inputOlderAgeMs] ago,
     * and [inputRecent], scored [inputRecentScore] now.
     */
    private fun recentlyUsedItems(
        inputOlder: Launchable,
        inputOlderScore: Double,
        inputOlderAgeMs: Long,
        inputRecent: Launchable,
        inputRecentScore: Double
    ): List<Launchable> {
        val inputCounts: Map<String, LaunchCountRecord> = mapOf(
            inputOlder.key to createLaunchCountRecord(
                key = inputOlder.key,
                launchCount = 10,
                recentUseScore = inputOlderScore,
                scoreUpdatedAt = TEST_NOW_EPOCH_MS - inputOlderAgeMs
            ),
            inputRecent.key to createLaunchCountRecord(
                key = inputRecent.key,
                launchCount = 3,
                recentUseScore = inputRecentScore,
                scoreUpdatedAt = TEST_NOW_EPOCH_MS
            )
        )
        return listOf(inputOlder, inputRecent).toAppListGroups(
            ranking = AppListRanking.RecentlyUsed(
                usage = createUsage(launchCountsByKey = inputCounts, rankFoldersByUse = false)
            )
        ).filterIsInstance<LaunchableGroup.InitialGroup>().single().items
    }

    private fun assertInitialGroupLabels(inputCase: LocaleGroupingCase) {
        val inputItems: List<Launchable> = inputCase.names.map { name -> createLaunchable(name) }

        val actualLabels: List<String> = inputItems.toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            locale = Locale.forLanguageTag(inputCase.localeTag)
        ).filterIsInstance<LaunchableGroup.InitialGroup>().map { group -> group.label }

        assertEquals(inputCase.toString(), inputCase.expectedLabels, actualLabels)
    }

    private fun List<Launchable>.toAppListGroups(
        ranking: AppListRanking,
        privateSpaceStatus: PrivateSpaceStatus = PrivateSpaceStatus()
    ): List<LaunchableGroup> = toLaunchableGroups(
        locale = Locale.US,
        builtinGroup = inputBuiltinGroup,
        privateSpaceStatus = privateSpaceStatus
    ).withAppListOrder(ranking = ranking)

    private fun createFolderRankingFixture(): FolderRankingFixture = FolderRankingFixture(
        app = createLaunchable("Beta"),
        otherApp = createLaunchable("Bouncer"),
        firstFolder = Folder(folderId = 1L, name = "Bravo folder"),
        secondFolder = Folder(folderId = 2L, name = "Boulder folder")
    )

    private fun FolderRankingFixture.createUsage(firstFolderCount: Int, secondFolderCount: Int, rankFoldersByUse: Boolean): AppListUsage =
        AppListUsage(
            launchCountsByKey = mapOf(
                app.key to createLaunchCountRecord(key = app.key, launchCount = 1),
                otherApp.key to createLaunchCountRecord(key = otherApp.key, launchCount = 2),
                firstFolder.key to createLaunchCountRecord(key = firstFolder.key, launchCount = firstFolderCount),
                secondFolder.key to createLaunchCountRecord(key = secondFolder.key, launchCount = secondFolderCount)
            ),
            rankFoldersByUse = rankFoldersByUse
        )

    private fun createUsage(launchCountsByKey: Map<String, LaunchCountRecord>, rankFoldersByUse: Boolean): AppListUsage =
        AppListUsage(launchCountsByKey = launchCountsByKey, rankFoldersByUse = rankFoldersByUse)

    private fun createLaunchable(name: String): Launchable = RailLaunchable(
        defaultName = name,
        defaultIcon = Icon.RailIcon(resId = TEST_RAIL_ICON_RES_ID),
        deeplink = "rail-launcher://$name"
    )
}

private data class FolderRankingFixture(val app: Launchable, val otherApp: Launchable, val firstFolder: Folder, val secondFolder: Folder) {
    val items: List<Launchable> get() = listOf(firstFolder, secondFolder, app, otherApp)
}
