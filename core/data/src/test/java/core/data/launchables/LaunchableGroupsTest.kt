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
    fun `keeps the Builtin group last`() {
        val inputItems: List<Launchable> = listOf(createLaunchable("Zulu"))
        val actualGroup: LaunchableGroup = inputItems.toLaunchableGroups(inputBuiltinGroup).last()
        assertEquals(inputBuiltinGroup, actualGroup)
    }

    @Test
    fun `flattens the App List groups into their Launchables and skips the Folders group`() {
        val inputGroups: List<LaunchableGroup> = listOf(
            LaunchableGroup.Star,
            LaunchableGroup.InitialGroup(label = "A", items = listOf(createLaunchable("Alpha"))),
            LaunchableGroup.Folders(items = listOf(Folder(folderId = 1L, showInAppList = true))),
            inputBuiltinGroup
        )
        val actualNames: List<String> = inputGroups.toAppListLaunchables().map { launchable -> launchable.name }
        assertEquals(listOf("Alpha", "Rail Settings"), actualNames)
    }

    @Test
    fun `groups Launchables by first character`() {
        val inputAlpha: Launchable = createLaunchable("Alpha")
        val inputAnchor: Launchable = createLaunchable("anchor")
        val actualGroup: LaunchableGroup = listOf(inputAlpha, inputAnchor).toLaunchableGroups(inputBuiltinGroup)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputAlpha, inputAnchor)), actualGroup)
    }

    @Test
    fun `places the Folders group after the letters and before Builtin`() {
        val inputItems: List<Launchable> = listOf(createLaunchable("Alpha"))
        val inputFolder: Folder = Folder(folderId = 1L, showInAppList = true)
        val actualLabels: List<String> = inputItems.toLaunchableGroups(inputBuiltinGroup, folders = listOf(inputFolder)).map { it.label }
        assertEquals(listOf("★", "A", "◇", "⌂"), actualLabels)
    }

    @Test
    fun `omits the Folders group when no Folder is shown in the App List`() {
        val inputFolder: Folder = Folder(folderId = 1L)
        val actualLabels: List<String> = emptyList<Launchable>()
            .toLaunchableGroups(inputBuiltinGroup, folders = listOf(inputFolder))
            .map { it.label }
        assertEquals(listOf("★", "⌂"), actualLabels)
    }

    @Test
    fun `shows only the Folders with the App List switch on`() {
        val inputShown: Folder = Folder(folderId = 1L, showInAppList = true)
        val inputHidden: Folder = Folder(folderId = 2L)
        val actualGroup: LaunchableGroup.Folders = emptyList<Launchable>()
            .toLaunchableGroups(inputBuiltinGroup, folders = listOf(inputShown, inputHidden))
            .filterIsInstance<LaunchableGroup.Folders>()
            .single()
        assertEquals(listOf(inputShown), actualGroup.items)
    }

    @Test
    fun `never groups a Folder by its first character`() {
        val inputFolder: Folder = Folder(folderId = 1L, name = "Zulu", showInAppList = true)
        val actualLabels: List<String> = emptyList<Launchable>()
            .toLaunchableGroups(inputBuiltinGroup, folders = listOf(inputFolder))
            .map { it.label }
        assertEquals(listOf("★", "◇", "⌂"), actualLabels)
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
