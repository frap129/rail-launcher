package core.data.shortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import core.data.apps.model.AppProfile
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableAppearance
import core.data.profiles.ProfileInfo
import core.util.openApplicationDetailsSettings

/** A launcher shortcut published by an app in a user profile. */
data class ShortcutLaunchable(
    val packageName: String,
    val shortcutId: String,
    override val profile: UserHandle,
    val shortLabel: String,
    val longLabel: String,
    val isEnabled: Boolean = true,
    /** Whether this is a placeholder for a stored Shortcut a Paused Profile cannot resolve yet. */
    val isPlaceholder: Boolean = false,
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

    override val supportsCustomIcon: Boolean = true

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

    fun openAppInfo(context: Context) {
        context.openApplicationDetailsSettings(packageName = packageName, profile = profile)
    }
}

/** Resolves [this] Shortcut published by [owner], applying Rail's label fallbacks. */
fun ShortcutInfo.toShortcutLaunchable(owner: AppProfile): ShortcutLaunchable {
    val shortLabel: String = shortLabel?.toString()?.takeIf { label -> label.isNotBlank() } ?: owner.packageName
    val longLabel: String = longLabel?.toString()?.takeIf { label -> label.isNotBlank() } ?: shortLabel
    return ShortcutLaunchable(
        packageName = owner.packageName,
        shortcutId = id,
        profile = owner.profile,
        shortLabel = shortLabel,
        longLabel = longLabel,
        isEnabled = isEnabled
    )
}
