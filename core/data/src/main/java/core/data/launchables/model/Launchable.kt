package core.data.launchables.model

import android.content.Context
import android.os.UserHandle
import core.data.icons.model.Icon

abstract class Launchable {
    abstract val key: String
    abstract val defaultName: String
    abstract val defaultIcon: Icon
    abstract val name: String
    abstract val icon: Icon

    /** The Profile this Launchable belongs to, or null when it belongs to none. */
    open val profile: UserHandle? get() = null

    /** Whether this Launchable belongs to a Paused Profile. */
    open val isPaused: Boolean get() = false

    /** Whether this Launchable's publisher currently disables it, so tapping it cannot open it. */
    open val isDisabled: Boolean get() = false

    /** Whether the user can choose a Custom icon for this Launchable. */
    open val supportsCustomIcon: Boolean get() = false

    /** The label accessibility services read for this Launchable. */
    open val accessibilityName: String get() = name

    /** The label accessibility services read: the name for the personal Profile, else the badged label. */
    protected fun accessibilityLabel(name: String, badgedLabel: String, isPersonal: Boolean): String = if (isPersonal) name else badgedLabel

    /** Returns a copy of this Launchable showing [appearance]. */
    abstract fun withCustomization(appearance: LaunchableAppearance): Launchable

    /** The customized name for this Launchable, falling back to its default. */
    fun resolveName(names: LaunchableNames): String = names[key] ?: defaultName

    abstract fun launch(context: Context)
}

/** The resolved name and icon shown for a Launchable. */
data class LaunchableAppearance(val name: String, val icon: Icon)
