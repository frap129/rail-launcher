package core.data.launchables.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppListKeyTest {
    @Test
    fun `keys an Initial group apart from the Builtin group sharing its label`() {
        val inputBuiltin: LaunchableGroup.Builtin = LaunchableGroup.Builtin(items = emptyList())
        val inputInitial: LaunchableGroup.InitialGroup = LaunchableGroup.InitialGroup(label = inputBuiltin.label, items = emptyList())
        val actualKeys: List<String> = listOf(inputInitial, inputBuiltin).map { group -> group.appListKey() }
        assertEquals(actualKeys.size, actualKeys.distinct().size)
    }

    @Test
    fun `keys Initial groups apart by label`() {
        val inputAlpha: LaunchableGroup.InitialGroup = LaunchableGroup.InitialGroup(label = "A", items = emptyList())
        val inputBeta: LaunchableGroup.InitialGroup = LaunchableGroup.InitialGroup(label = "B", items = emptyList())
        val actualKeys: List<String> = listOf(inputAlpha, inputBeta).map { group -> group.appListKey() }
        assertEquals(actualKeys.size, actualKeys.distinct().size)
    }

    @Test
    fun `keys the Folders group apart from an Initial group named with its Rail glyph`() {
        val inputFolders: LaunchableGroup.Folders = LaunchableGroup.Folders(items = emptyList())
        val inputInitial: LaunchableGroup.InitialGroup = LaunchableGroup.InitialGroup(label = inputFolders.label, items = emptyList())
        val actualKeys: List<String> = listOf(inputInitial, inputFolders).map { group -> group.appListKey() }
        assertEquals(listOf("initial:◇", "folders"), actualKeys)
    }
}
