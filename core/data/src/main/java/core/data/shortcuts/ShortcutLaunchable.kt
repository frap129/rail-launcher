package core.data.shortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableAppearance
import core.data.profiles.ProfileInfo

/** A launcher shortcut published by an app in a user profile. */
data class ShortcutLaunchable(
    val packageName: String,
    val shortcutId: String,
    override val profile: UserHandle,
    val shortLabel: String,
    val longLabel: String,
    val isEnabled: Boolean = true,
    val profileInfo: ProfileInfo? = null,
    override val defaultName: String = longLabel.ifBlank { shortLabel },
    override val name: String = defaultName,
    override val defaultIcon: Icon = Icon.ShortcutIcon(packageName = packageName, shortcutId = shortcutId, profile = profile),
    override val icon: Icon = defaultIcon,
    val badgedLabel: String = defaultName
) : Launchable() {
    override val key: String = ShortcutKey(packageName = packageName, id = shortcutId, profile = profile).format()

    override val isPaused: Boolean get() = profileInfo?.isQuiet == true

    override val isDisabled: Boolean get() = !isEnabled

    override val accessibilityName: String
        get() = accessibilityLabel(
            name = name,
            badgedLabel = badgedLabel,
            isPersonal = profileInfo?.kind?.isPersonal != false
        )

    override fun withCustomization(appearance: LaunchableAppearance): ShortcutLaunchable =
        copy(name = appearance.name, icon = appearance.icon)

    override fun launch(context: Context) {
        val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
        launcherApps.startShortcut(packageName, shortcutId, null, null, profile)
    }
}
