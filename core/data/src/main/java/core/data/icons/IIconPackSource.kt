package core.data.icons

import core.util.DrawableCategory

/** Reads the drawables an installed icon pack offers. */
interface IIconPackSource {
    /** The drawable categories the pack lists, dropping any names that do not resolve. */
    fun drawableCategories(packPackageName: String): List<DrawableCategory>

    /** Whether [packPackageName] still provides [drawableName]. */
    fun hasDrawable(packPackageName: String, drawableName: String): Boolean

    /** The drawable name [resId] resolves to in [packPackageName], or null when the pack no longer provides it. */
    fun resolveDrawableName(packPackageName: String, resId: Int): String?
}
