package core.data.icons

/** Reads the drawables an installed icon pack offers. */
interface IIconPackSource {
    /** The drawable names the pack lists, dropping any that do not resolve. */
    fun drawableNames(packPackageName: String): List<String>

    /** Whether [packPackageName] still provides [drawableName]. */
    fun hasDrawable(packPackageName: String, drawableName: String): Boolean
}
