package core.data.launchables

import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import org.junit.Assert.assertEquals
import org.junit.Test

private const val RAIL_ICON_RES_ID: Int = 0

class LaunchableGroupsTest {
    private val builtinGroup: LaunchableGroup.Builtin = LaunchableGroup.Builtin(items = listOf(createLaunchable("Rail Settings")))

    @Test
    fun `places the Star above A`() {
        val inputItems: List<Launchable> = listOf(createLaunchable("Beta"), createLaunchable("Alpha"))
        val actual: List<String> = inputItems.toLaunchableGroups(builtinGroup).map { it.label }
        assertEquals(listOf("★", "A", "B", "⌂"), actual)
    }

    @Test
    fun `places the Star above #`() {
        val inputItems: List<Launchable> = listOf(createLaunchable("Alpha"), createLaunchable("1Password"))
        val actual: List<String> = inputItems.toLaunchableGroups(builtinGroup).map { it.label }
        assertEquals(listOf("★", "#", "A", "⌂"), actual)
    }

    @Test
    fun `groups names starting with any digit under #`() {
        val inputZero: Launchable = createLaunchable("0xBench")
        val inputOne: Launchable = createLaunchable("1Password")
        val inputNine: Launchable = createLaunchable("9GAG")
        val actual: LaunchableGroup = listOf(inputZero, inputOne, inputNine).toLaunchableGroups(builtinGroup)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "#", items = listOf(inputZero, inputOne, inputNine)), actual)
    }

    @Test
    fun `groups names starting with a non-ASCII digit under #`() {
        val inputItem: Launchable = createLaunchable("１Password")
        val actual: LaunchableGroup = listOf(inputItem).toLaunchableGroups(builtinGroup)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "#", items = listOf(inputItem)), actual)
    }

    @Test
    fun `groups names with no initial character under #`() {
        val inputItem: Launchable = createLaunchable("")
        val actual: LaunchableGroup = listOf(inputItem).toLaunchableGroups(builtinGroup)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "#", items = listOf(inputItem)), actual)
    }

    @Test
    fun `keeps the Builtin group last`() {
        val inputItems: List<Launchable> = listOf(createLaunchable("Zulu"))
        val actual: LaunchableGroup = inputItems.toLaunchableGroups(builtinGroup).last()
        assertEquals(builtinGroup, actual)
    }

    @Test
    fun `flattens the Listed groups into their Launchables in Rail order`() {
        val inputGroups: List<LaunchableGroup> = listOf(
            LaunchableGroup.Star,
            LaunchableGroup.InitialGroup(label = "A", items = listOf(createLaunchable("Alpha"))),
            builtinGroup
        )
        val actualNames: List<String> = inputGroups.toLaunchables().map { launchable -> launchable.name }
        assertEquals(listOf("Alpha", "Rail Settings"), actualNames)
    }

    @Test
    fun `groups Launchables by first character`() {
        val inputAlpha: Launchable = createLaunchable("Alpha")
        val inputAnchor: Launchable = createLaunchable("anchor")
        val actual: LaunchableGroup = listOf(inputAlpha, inputAnchor).toLaunchableGroups(builtinGroup)[1]
        assertEquals(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputAlpha, inputAnchor)), actual)
    }

    @Test
    fun `keeps the Star and Builtin groups when there are no Launchables`() {
        val actual: List<LaunchableGroup> = emptyList<Launchable>().toLaunchableGroups(builtinGroup)
        assertEquals(listOf(LaunchableGroup.Star, builtinGroup), actual)
    }

    private fun createLaunchable(name: String): Launchable = RailLaunchable(
        defaultName = name,
        defaultIcon = Icon.RailIcon(resId = RAIL_ICON_RES_ID),
        deeplink = "rail-launcher://$name"
    )
}
