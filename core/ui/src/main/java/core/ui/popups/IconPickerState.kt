package core.ui.popups

import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.icons.model.IconPackCategory
import core.data.launchables.model.Launchable
import core.util.toNormalizedDrawableName

/** Whether the Icon picker is showing and, when it is, what it lists. */
sealed interface IconPickerState {
    data object Closed : IconPickerState

    /** The picker listing the Suggested icons, Original, Gallery and the installed icon packs. */
    data class Listing(
        val owner: Launchable,
        val iconPacks: List<IconPack.CustomIconPack>,
        val suggestions: List<Icon>,
        val isImportingImage: Boolean = false,
        val imageImportFailed: Boolean = false
    ) : IconPickerState

    /**
     * One pack's icons; [categories] is null while they load. [results] holds the flat matches for
     * [query] and is empty while no query filters them.
     */
    data class Pack(
        val iconPack: IconPack.CustomIconPack,
        val categories: List<IconPackCategory>?,
        val query: String = "",
        val results: List<Icon.PackDrawableIcon> = emptyList()
    ) : IconPickerState
}

/** The query with underscores read as spaces and surrounding spaces trimmed; empty when it filters nothing. */
internal fun normalizeIconPickerQuery(query: String): String = query.toNormalizedDrawableName().trim()

/** Whether the pack's query filters its icons. */
internal val IconPickerState.Pack.isFiltering: Boolean
    get() = normalizeIconPickerQuery(query).isNotEmpty()
