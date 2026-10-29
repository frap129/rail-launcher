package core.ui

import core.data.launchables.model.LaunchableGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupMarkTest {
    @Test
    fun `draws Private Space as a shield on the Rail and in the App List header`() {
        val inputLocked: LaunchableGroup.PrivateSpace = LaunchableGroup.PrivateSpace.Locked
        val inputUnlocked: LaunchableGroup.PrivateSpace = LaunchableGroup.PrivateSpace.Unlocked(items = emptyList())

        assertEquals(GroupMark.PrivateSpaceShield, inputLocked.toGroupMark())
        assertEquals(GroupMark.PrivateSpaceShield, inputUnlocked.toGroupMark())
    }

    @Test
    fun `draws the other groups with their labels`() {
        val inputInitial: LaunchableGroup.InitialGroup = LaunchableGroup.InitialGroup(label = "A", items = emptyList())
        val inputBuiltin: LaunchableGroup.Builtin = LaunchableGroup.Builtin(items = emptyList())

        assertEquals(GroupMark.Label(LaunchableGroup.Star.label), LaunchableGroup.Star.toGroupMark())
        assertEquals(GroupMark.Label(inputInitial.label), inputInitial.toGroupMark())
        assertEquals(GroupMark.Label(inputBuiltin.label), inputBuiltin.toGroupMark())
    }
}
