package core.data.launchables

import core.data.folders.Folder
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import org.junit.Assert.assertEquals
import org.junit.Test

private const val RAIL_ICON_RES_ID: Int = 0

class LaunchableGroupsTest {
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

    private fun createLaunchable(name: String): Launchable = RailLaunchable(
        defaultName = name,
        defaultIcon = Icon.RailIcon(resId = RAIL_ICON_RES_ID),
        deeplink = "rail-launcher://$name"
    )
}
