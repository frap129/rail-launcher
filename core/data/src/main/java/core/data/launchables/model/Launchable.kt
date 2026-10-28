package core.data.launchables.model

import android.content.Context
import core.data.icons.model.Icon

abstract class Launchable {
    abstract val key: String
    abstract val defaultName: String
    abstract val defaultIcon: Icon
    abstract val name: String
    abstract val icon: Icon

    /** The label accessibility services read for this Launchable. */
    open val accessibilityName: String get() = name

    /** Returns a copy of this Launchable showing [appearance]. */
    abstract fun withCustomization(appearance: LaunchableAppearance): Launchable

    /** The customized name for this Launchable, falling back to its default. */
    fun resolveName(names: LaunchableNames): String = names[key] ?: defaultName

    abstract fun launch(context: Context)
}

/** The resolved name and icon shown for a Launchable. */
data class LaunchableAppearance(val name: String, val icon: Icon)
