package core.data.launchables

import core.data.folders.Folder
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.launches.LaunchCountRecord
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_FOURTEEN_DAYS_MS
import core.data.testing.TEST_NOW_EPOCH_MS
import core.data.testing.TEST_RAIL_ICON_RES_ID
import core.data.testing.TEST_SDK
import core.data.testing.createBuiltinGroup
import core.data.testing.createFixedPrivateSpaceStatus
import core.data.testing.createPrivateApp
import core.data.testing.createProfileInfo
import core.data.testing.createShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val actualLabels: List<String> = inputItems.toLaunchableGroups(inputBuiltinGroup).map { it.label }
        assertEquals(listOf("★", "A", "B", "⌂"), actualLabels)
    }

    @Test
    fun `places the Star above #`() {
        val inputItems: List<Launchable> = listOf(createLaunchable("Alpha"), createLaunchable("1Password"))
        val actualLabels: List<String> = inputItems.toLaunchableGroups(inputBuiltinGroup).map { it.label }
        assertEquals(listOf("★", "#", "A", "⌂"), actualLabels)
    }

    @Test
    fun `groups names starting with any digit under #`() {
        val inputZero: Launchable = createLaunchable("0xBench")
        val inputOne: Launchable = createLaunchable("1Password")
        val inputNine: Launchable = createLaunchable("9GAG")
        val actualGroup: LaunchableGroup = listOf(inputZero, inputOne, inputNine).toLaunchableGroups(inputBuiltinGroup)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "#", items = listOf(inputZero, inputOne, inputNine)), actualGroup)
    }

    @Test
    fun `groups names starting with a non-ASCII digit under #`() {
        val inputItem: Launchable = createLaunchable("１Password")
        val actualGroup: LaunchableGroup = listOf(inputItem).toLaunchableGroups(inputBuiltinGroup)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "#", items = listOf(inputItem)), actualGroup)
    }

    @Test
    fun `groups names with no initial character under #`() {
        val inputItem: Launchable = createLaunchable("")
        val actualGroup: LaunchableGroup = listOf(inputItem).toLaunchableGroups(inputBuiltinGroup)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "#", items = listOf(inputItem)), actualGroup)
    }

    @Test
    fun `places a Folder in the alphabetical group for its resolved name`() {
        val inputFolder: Folder = Folder(folderId = 1L, name = "Zulu")
        val inputItems: List<Launchable> = listOf(createLaunchable("Alpha"), inputFolder)
        val actualGroups: List<LaunchableGroup> = inputItems.toLaunchableGroups(inputBuiltinGroup)

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
        val actualGroup: LaunchableGroup = listOf(inputFolder).toLaunchableGroups(inputBuiltinGroup)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "#", items = listOf(inputFolder)), actualGroup)
    }

    @Test
    fun `groups a Folder with no initial character under #`() {
        val inputFolder: Folder = Folder(folderId = 1L, name = "")
        val actualGroup: LaunchableGroup = listOf(inputFolder).toLaunchableGroups(inputBuiltinGroup)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "#", items = listOf(inputFolder)), actualGroup)
    }

    @Test
    fun `groups Launchables by first character`() {
        val inputAlpha: Launchable = createLaunchable("Alpha")
        val inputAnchor: Launchable = createLaunchable("anchor")
        val actualGroup: LaunchableGroup = listOf(inputAlpha, inputAnchor).toLaunchableGroups(inputBuiltinGroup)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputAlpha, inputAnchor)), actualGroup)
    }

    @Test
    fun `keeps the Builtin group last`() {
        val inputItems: List<Launchable> = listOf(createLaunchable("Zulu"))
        val actualGroup: LaunchableGroup = inputItems.toLaunchableGroups(inputBuiltinGroup).last()
        assertEquals(inputBuiltinGroup, actualGroup)
    }

    @Test
    fun `keeps the Star and Builtin groups when there are no Launchables`() {
        val actualGroups: List<LaunchableGroup> = emptyList<Launchable>().toLaunchableGroups(inputBuiltinGroup)
        assertEquals(listOf(LaunchableGroup.Star, inputBuiltinGroup), actualGroups)
    }

    @Test
    fun `places the Private Space group after the Builtin group`() {
        val inputItems: List<Launchable> = listOf(createLaunchable("Alpha"))

        val actualLabels: List<String> = inputItems.toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            privateSpaceStatus = createFixedPrivateSpaceStatus(profile = inputPrivateProfile, hasListedApps = true)
        ).map { group -> group.label }

        assertEquals(listOf("★", "A", "⌂", LaunchableGroup.PrivateSpace.LABEL), actualLabels)
    }

    @Test
    fun `gives a Locked Private Space no items`() {
        val inputPrivateApp: Launchable = createPrivateApp(name = "Secret")

        val actualGroup: LaunchableGroup.PrivateSpace = listOf(inputPrivateApp).toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            privateSpaceStatus = createFixedPrivateSpaceStatus(profile = inputPrivateProfile, isLocked = true)
        ).filterIsInstance<LaunchableGroup.PrivateSpace>().single()

        assertTrue(actualGroup.isLocked)
        assertTrue(actualGroup.items.isEmpty())
    }

    @Test
    fun `leaves a Hidden Private Space off the groups`() {
        val actualGroups: List<LaunchableGroup> = emptyList<Launchable>().toLaunchableGroups(
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

        val actualGroup: LaunchableGroup.PrivateSpace = listOf(inputZulu, inputAlpha).toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            privateSpaceStatus = createFixedPrivateSpaceStatus(profile = inputPrivateProfile, hasListedApps = true)
        ).filterIsInstance<LaunchableGroup.PrivateSpace>().single()

        assertFalse(actualGroup.isLocked)
        assertEquals(listOf(inputAlpha, inputZulu), actualGroup.items)
    }

    @Test
    fun `leaves the Private Space group off without a Private Space`() {
        val inputPrivateApp: Launchable = createPrivateApp(name = "Secret")

        val actualGroups: List<LaunchableGroup> = listOf(inputPrivateApp).toLaunchableGroups(builtinGroup = inputBuiltinGroup)

        assertEquals(listOf("★", "⌂"), actualGroups.map { group -> group.label })
        assertTrue(actualGroups.none { group -> group is LaunchableGroup.PrivateSpace })
    }

    @Test
    fun `keeps private Apps out of the Initial groups`() {
        val inputPrivateApp: Launchable = createPrivateApp(name = "Secret")

        val actualGroups: List<LaunchableGroup> = listOf(inputPrivateApp, createLaunchable("Alpha")).toLaunchableGroups(
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
    fun `Alphabetical keeps the input order`() {
        val inputZulu: Launchable = createLaunchable("Zulu")
        val inputZebra: Launchable = createLaunchable("Zebra")

        val actualGroup: LaunchableGroup = listOf(inputZulu, inputZebra).toLaunchableGroups(builtinGroup = inputBuiltinGroup)[1]

        assertEquals(LaunchableGroup.InitialGroup(label = "Z", items = listOf(inputZulu, inputZebra)), actualGroup)
    }

    @Test
    fun `Most used orders by Launch count with case-insensitive A-Z ties and never-launched items last`() {
        val inputAlpine: Launchable = createLaunchable("Alpine")
        val inputAlfred: Launchable = createLaunchable("alfred")
        val inputAmber: Launchable = createLaunchable("Amber")
        val inputAzure: Launchable = createLaunchable("azure")
        val inputCounts: Map<String, LaunchCountRecord> = mapOf(
            inputAlpine.key to createLaunchCount(key = inputAlpine.key, launchCount = 3),
            inputAlfred.key to createLaunchCount(key = inputAlfred.key, launchCount = 3)
        )

        val actualGroup: LaunchableGroup = listOf(inputAzure, inputAmber, inputAlpine, inputAlfred).toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            ranking = AppListRanking.MostUsed(launchCountsByKey = inputCounts)
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
        val inputCounts: Map<String, LaunchCountRecord> = mapOf(
            inputOlder.key to createLaunchCount(
                key = inputOlder.key,
                launchCount = 10,
                recentUseScore = 10.0,
                scoreUpdatedAt = TEST_NOW_EPOCH_MS - TEST_FOURTEEN_DAYS_MS
            ),
            inputRecent.key to createLaunchCount(
                key = inputRecent.key,
                launchCount = 3,
                recentUseScore = 3.0,
                scoreUpdatedAt = TEST_NOW_EPOCH_MS
            )
        )

        val actualGroup: LaunchableGroup = listOf(inputOlder, inputRecent).toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            ranking = AppListRanking.RecentlyUsed(launchCountsByKey = inputCounts, nowEpochMs = TEST_NOW_EPOCH_MS)
        )[1]

        assertEquals(LaunchableGroup.InitialGroup(label = "O", items = listOf(inputRecent, inputOlder)), actualGroup)
    }

    @Test
    fun `Most used keeps Folders last in their current order`() {
        val inputApp: Launchable = createLaunchable("Beta")
        val inputOtherApp: Launchable = createLaunchable("Bouncer")
        val inputFirstFolder: Folder = Folder(folderId = 1L, name = "Bravo folder")
        val inputSecondFolder: Folder = Folder(folderId = 2L, name = "Boulder folder")
        val inputCounts: Map<String, LaunchCountRecord> = mapOf(
            inputApp.key to createLaunchCount(key = inputApp.key, launchCount = 1),
            inputOtherApp.key to createLaunchCount(key = inputOtherApp.key, launchCount = 2),
            inputFirstFolder.key to createLaunchCount(key = inputFirstFolder.key, launchCount = 99),
            inputSecondFolder.key to createLaunchCount(key = inputSecondFolder.key, launchCount = 98)
        )

        val actualGroup: LaunchableGroup = listOf(inputFirstFolder, inputSecondFolder, inputApp, inputOtherApp).toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            ranking = AppListRanking.MostUsed(launchCountsByKey = inputCounts)
        )[1]

        assertEquals(
            LaunchableGroup.InitialGroup(
                label = "B",
                items = listOf(inputOtherApp, inputApp, inputFirstFolder, inputSecondFolder)
            ),
            actualGroup
        )
    }

    @Test
    fun `Most used ranks the Unlocked Private Space group`() {
        val inputZulu: Launchable = createPrivateApp(name = "Zulu", packageName = "com.example.zulu")
        val inputAlpha: Launchable = createPrivateApp(name = "alpha", packageName = "com.example.alpha")
        val inputCounts: Map<String, LaunchCountRecord> = mapOf(
            inputZulu.key to createLaunchCount(key = inputZulu.key, launchCount = 5),
            inputAlpha.key to createLaunchCount(key = inputAlpha.key, launchCount = 1)
        )

        val actualGroup: LaunchableGroup.PrivateSpace = listOf(inputAlpha, inputZulu).toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            privateSpaceStatus = createFixedPrivateSpaceStatus(profile = inputPrivateProfile, hasListedApps = true),
            ranking = AppListRanking.MostUsed(launchCountsByKey = inputCounts)
        ).filterIsInstance<LaunchableGroup.PrivateSpace>().single()

        assertEquals(listOf(inputZulu, inputAlpha), actualGroup.items)
    }

    @Test
    fun `keeps the group order and the Builtin group while ranking by use`() {
        val inputZulu: Launchable = createLaunchable("Zulu")
        val inputAlpha: Launchable = createLaunchable("Alpha")
        val inputCounts: Map<String, LaunchCountRecord> = mapOf(
            inputAlpha.key to createLaunchCount(key = inputAlpha.key, launchCount = 3)
        )

        val actualGroups: List<LaunchableGroup> = listOf(inputZulu, inputAlpha).toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            ranking = AppListRanking.MostUsed(launchCountsByKey = inputCounts)
        )

        assertEquals(listOf("★", "A", "Z", "⌂"), actualGroups.map { group -> group.label })
        assertEquals(inputBuiltinGroup, actualGroups.last())
    }

    private fun createLaunchCount(
        key: String,
        launchCount: Int,
        recentUseScore: Double = launchCount.toDouble(),
        scoreUpdatedAt: Long = TEST_NOW_EPOCH_MS
    ): LaunchCountRecord = LaunchCountRecord(
        key = key,
        launchCount = launchCount,
        recentUseScore = recentUseScore,
        scoreUpdatedAt = scoreUpdatedAt
    )

    private fun createLaunchable(name: String): Launchable = RailLaunchable(
        defaultName = name,
        defaultIcon = Icon.RailIcon(resId = TEST_RAIL_ICON_RES_ID),
        deeplink = "rail-launcher://$name"
    )
}
