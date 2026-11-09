package core.data.icons

import core.util.DrawableCategory

/** Reads the drawables an installed icon pack offers. */
interface IIconPackSource {
    /** The drawable categories the pack lists, dropping any names that do not resolve. */
    fun drawableCategories(packPackageName: String): List<DrawableCategory>

    /** Whether [packPackageName] still provides [drawableName]. */
    fun hasDrawable(packPackageName: String, drawableName: String): Boolean
}
