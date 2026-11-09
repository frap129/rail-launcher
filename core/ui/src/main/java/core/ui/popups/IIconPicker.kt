package core.ui.popups

import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import kotlinx.coroutines.flow.StateFlow

/** Owns the Icon picker and the Custom icon writes it commits. */
interface IIconPicker {
    /** What the Icon picker shows. */
    val iconPickerState: StateFlow<IconPickerState>

    /** Opens the picker for [owner]. */
    fun startPicking(owner: Launchable)

    /** Opens [iconPack]'s icons inside the open picker. */
    fun openPack(iconPack: IconPack.CustomIconPack)

    /** Filters the open pack's icons by [query]. */
    fun setQuery(query: String)

    /** Applies [icon] as the owner's Custom icon and closes the picker. */
    fun selectIcon(icon: Icon.PackDrawableIcon)

    /** Removes the owner's Custom icon and closes the picker. */
    fun resetIcon()

    /** Closes the picker without changing the owner's icon. */
    fun cancelPicking()

    /** Closes the picker when the Pop-up closes. */
    fun onCardClosed()

    /** Closes the picker when the card opens over it. */
    fun onCardOpened()

    /** Closes the picker when another sheet opens. */
    fun onEditStarted()

    /** Closes the picker when the editor closes. */
    fun onEditFinished()

    /** Steps out of a pack, then closes the picker; returns false when it is closed already. */
    fun backBeforeItemMenu(): Boolean

    /** Stops this collaborator's flows. */
    fun release()
}
