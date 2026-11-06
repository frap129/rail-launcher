package core.ui.popups

import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable

/** Whether the Icon picker is showing and, when it is, what it lists. */
sealed interface IconPickerState {
    data object Closed : IconPickerState

    /** The picker listing Original and the installed icon packs. */
    data class Listing(val owner: Launchable, val iconPacks: List<IconPack.CustomIconPack>) : IconPickerState

    /** One pack's icons; [icons] is null while its contents load. */
    data class Pack(val iconPack: IconPack.CustomIconPack, val icons: List<Icon.PackDrawableIcon>?) : IconPickerState
}
