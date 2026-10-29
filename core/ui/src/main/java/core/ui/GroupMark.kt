package core.ui

import core.data.launchables.model.LaunchableGroup

/** The mark a group draws on the Rail and above its App List section. */
sealed interface GroupMark {
    /** A text label. */
    data class Label(val text: String) : GroupMark

    /** The Private Space shield. */
    data object PrivateSpaceShield : GroupMark
}

fun LaunchableGroup.toGroupMark(): GroupMark = when (this) {
    is LaunchableGroup.PrivateSpace -> GroupMark.PrivateSpaceShield
    else -> GroupMark.Label(label)
}
