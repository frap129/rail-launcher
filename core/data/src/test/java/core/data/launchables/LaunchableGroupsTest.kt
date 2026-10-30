package core.data.launchables

import core.data.folders.Folder
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_RAIL_ICON_RES_ID
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createProfileInfo
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
    private val inputBuiltinGroup: LaunchableGroup.Builtin = LaunchableGroup.Builtin(items = listOf(createLaunchable("Rail Settings")))

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
            privateSpace = inputPrivateProfile
        ).map { group -> group.label }

        assertEquals(listOf("★", "A", "⌂", LaunchableGroup.PrivateSpace.LABEL), actualLabels)
    }

    @Test
    fun `gives a Locked Private Space no items`() {
        val inputPrivateApp: Launchable = createPrivateApp(name = "Secret")

        val actualGroup: LaunchableGroup.PrivateSpace = listOf(inputPrivateApp).toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            privateSpace = inputPrivateProfile.copy(isQuiet = true)
        ).filterIsInstance<LaunchableGroup.PrivateSpace>().single()

        assertTrue(actualGroup.isLocked)
        assertTrue(actualGroup.items.isEmpty())
    }

    @Test
    fun `gives a Private Space with a Lock requested no items`() {
        val inputPrivateApp: Launchable = createPrivateApp(name = "Secret")

        val actualGroup: LaunchableGroup.PrivateSpace = listOf(inputPrivateApp).toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            privateSpace = inputPrivateProfile,
            isLockRequested = true
        ).filterIsInstance<LaunchableGroup.PrivateSpace>().single()

        assertTrue(actualGroup.isLocked)
        assertTrue(actualGroup.items.isEmpty())
    }

    @Test
    fun `hides a Private Space with a Lock requested while Rail hides it`() {
        val actualGroups: List<LaunchableGroup> = emptyList<Launchable>().toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            privateSpace = inputPrivateProfile,
            hidePrivateSpaceWhenLocked = true,
            isLockRequested = true
        )

        assertEquals(listOf("★", "⌂"), actualGroups.map { group -> group.label })
        assertTrue(actualGroups.none { group -> group is LaunchableGroup.PrivateSpace })
    }

    @Test
    fun `hides a Locked Private Space while Rail hides it`() {
        val actualGroups: List<LaunchableGroup> = emptyList<Launchable>().toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            privateSpace = inputPrivateProfile.copy(isQuiet = true),
            hidePrivateSpaceWhenLocked = true
        )

        assertEquals(listOf("★", "⌂"), actualGroups.map { group -> group.label })
        assertTrue(actualGroups.none { group -> group is LaunchableGroup.PrivateSpace })
    }

    @Test
    fun `hides a Locked Private Space while the system hides it`() {
        val actualGroups: List<LaunchableGroup> = emptyList<Launchable>().toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            privateSpace = inputPrivateProfile.copy(isQuiet = true, isEntrypointHidden = true)
        )

        assertEquals(listOf("★", "⌂"), actualGroups.map { group -> group.label })
        assertTrue(actualGroups.none { group -> group is LaunchableGroup.PrivateSpace })
    }

    @Test
    fun `shows an Unlocked Private Space while Rail and the system hide it`() {
        val inputPrivateApp: Launchable = createPrivateApp(name = "Secret")

        val actualGroup: LaunchableGroup.PrivateSpace = listOf(inputPrivateApp).toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            privateSpace = inputPrivateProfile.copy(isEntrypointHidden = true),
            hidePrivateSpaceWhenLocked = true
        ).filterIsInstance<LaunchableGroup.PrivateSpace>().single()

        assertFalse(actualGroup.isLocked)
        assertEquals(listOf(inputPrivateApp), actualGroup.items)
    }

    @Test
    fun `lists private Apps A-Z in an Unlocked Private Space`() {
        val inputZulu: Launchable = createPrivateApp(name = "Zulu", packageName = "com.example.zulu")
        val inputAlpha: Launchable = createPrivateApp(name = "alpha", packageName = "com.example.alpha")

        val actualGroup: LaunchableGroup.PrivateSpace = listOf(inputZulu, inputAlpha).toLaunchableGroups(
            builtinGroup = inputBuiltinGroup,
            privateSpace = inputPrivateProfile
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
            privateSpace = inputPrivateProfile
        )
        val actualInitialItems: List<Launchable> = actualGroups.filterIsInstance<LaunchableGroup.InitialGroup>()
            .flatMap { group -> group.items }

        assertFalse(actualInitialItems.contains(inputPrivateApp))
    }

    @Test
    fun `gives the Private Space group a label apart from the other Rail glyphs and initials`() {
        val actualLabel: String = LaunchableGroup.PrivateSpace.LABEL
        val inputOtherLabels: List<String> = listOf(LaunchableGroup.Star.label, inputBuiltinGroup.label)

        assertFalse(actualLabel in inputOtherLabels)
        assertFalse(actualLabel.first().isLetterOrDigit())
    }

    private fun createPrivateApp(name: String, packageName: String = "com.example.secret"): Launchable = createApp(
        name = name,
        packageName = packageName,
        profileInfo = inputPrivateProfile
    )

    private fun createLaunchable(name: String): Launchable = RailLaunchable(
        defaultName = name,
        defaultIcon = Icon.RailIcon(resId = TEST_RAIL_ICON_RES_ID),
        deeplink = "rail-launcher://$name"
    )
}
