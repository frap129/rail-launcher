package core.data.apps.model

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import core.data.icons.model.Icon
import core.data.icons.model.badgedFor
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableAppearance
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.util.openApplicationDetailsSettings

data class App(
    override val defaultName: String,
    val profileInfo: ProfileInfo,
    val componentName: ComponentName,
    val badgedLabel: String = defaultName,
    override val name: String = defaultName,
    override val defaultIcon: Icon = Icon.ActivityIcon(componentName).badgedFor(profile = profileInfo),
    override val icon: Icon = defaultIcon
) : Launchable() {
    val profile: UserHandle = profileInfo.user
    val kind: ProfileKind = profileInfo.kind
    val isPaused: Boolean = profileInfo.isQuiet
    val packageName: String = componentName.packageName

    /** The App's stored identity: its package name and Profile. */
    override val key: String = "${packageName}_$profile"
    override val accessibilityName: String get() = if (kind.isPersonal) name else badgedLabel

    override fun withCustomization(appearance: LaunchableAppearance): App = copy(name = appearance.name, icon = appearance.icon)

    override fun launch(context: Context) {
        val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
        launcherApps.startMainActivity(componentName, profile, null, null)
    }

    fun openAppInfo(context: Context) {
        context.openApplicationDetailsSettings(componentName = componentName, profile = profile)
    }
}

/** Whether this Launchable is a private App, listed only inside the Private Space for now. */
val Launchable.isPrivateApp: Boolean get() = this is App && kind == ProfileKind.Private

/**
 * One App per package, choosing the alphabetically first activity class as the package's
 * representative so the choice does not depend on the order the App source lists activities in.
 * The App's label, icon and launch target all come from that same activity; a package's other
 * launcher activities are not listed.
 */
fun List<App>.onePerPackage(): List<App> =
    groupBy { app -> app.packageName }.values.map { apps -> apps.minBy { app -> app.componentName.className } }
