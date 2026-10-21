package core.data.launchables.model

import android.content.Context
import core.data.icons.model.Icon

abstract class Launchable {
    abstract val key: String
    abstract val defaultName: String
    abstract val defaultIcon: Icon
    abstract val name: String
    abstract val icon: Icon

    /** Returns a copy of this Launchable showing [appearance]. */
    abstract fun withCustomization(appearance: LaunchableAppearance): Launchable

    abstract fun launch(context: Context)
}

/** The resolved name and icon shown for a Launchable. */
data class LaunchableAppearance(val name: String, val icon: Icon)
