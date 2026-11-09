package core.data.icons.model

/** One category of an Icon pack's icons; [name] is null when the pack lists it without a heading. */
data class IconPackCategory(val name: String?, val icons: List<Icon.PackDrawableIcon>)
