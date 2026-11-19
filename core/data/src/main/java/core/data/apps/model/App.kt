package core.data.apps.model

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import core.data.icons.model.Icon
import core.data.icons.model.activityIconFor
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableAppearance
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.util.openApplicationDetailsSettings
import core.util.requestUninstall

data class App(
    override val defaultName: String,
    val profileInfo: ProfileInfo,
    val componentName: ComponentName,
    val badgedLabel: String = defaultName,
    override val name: String = defaultName,
    /**
     * Whether this App is its package's representative in its Profile: the alphabetically first
     * launcher activity, which owns the package's Shortcuts and notifications.
     */
    val isPackageRepresentative: Boolean,
    /** When the App's package was first installed, in epoch milliseconds, or 0 when unknown. */
    val firstInstallTime: Long,
    /** When the App's package was last installed or updated, in epoch milliseconds, or 0 when unknown. */
    val lastUpdateTime: Long,
    /** Whether the system lets the user uninstall the App's package from its Profile. */
    val isRemovable: Boolean,
    /** Whether the App is a System App, preinstalled on the system image, updated or not. */
    val isSystemApp: Boolean,
    /** Whether the system has suspended the App's package, as Digital Wellbeing or a freezer app does. */
    override val isSuspended: Boolean = false,
    /** The App's [lastUpdateTime] rides on its icon, so an App update never shows a stored Home icon. */
    override val defaultIcon: Icon = activityIconFor(
        componentName = componentName,
        profile = profileInfo,
        lastUpdateTime = lastUpdateTime
    ),
    override val icon: Icon = defaultIcon
) : Launchable() {
    override val profile: UserHandle = profileInfo.user
    val kind: ProfileKind = profileInfo.kind
    override val isPaused: Boolean = profileInfo.isQuiet
    override val supportsCustomIcon: Boolean = true
    val packageName: String = componentName.packageName

    /** The App's stored identity: its activity component and Profile. */
    override val key: String = "${componentName.flattenToString()}_$profile"
    override val accessibilityName: String
        get() = accessibilityLabel(name = name, badgedLabel = badgedLabel, isPersonal = kind.isPersonal)

    override fun withCustomization(appearance: LaunchableAppearance): App = copy(name = appearance.name, icon = appearance.icon)

    override fun launch(context: Context) {
        val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
        launcherApps.startMainActivity(componentName, profile, null, null)
    }

    fun openAppInfo(context: Context) {
        context.openApplicationDetailsSettings(componentName = componentName, profile = profile)
    }

    /** Whether the user may uninstall the App from its Profile. */
    fun canUninstall(profileSource: IProfileSource): Boolean = isRemovable && profileSource.canUninstall(user = profile)

    fun requestUninstall(context: Context) {
        context.requestUninstall(packageName = packageName, profile = profile)
    }
}

/** Whether this Launchable is a private App, listed only inside the Private Space for now. */
val Launchable.isPrivateApp: Boolean get() = this is App && kind == ProfileKind.Private

/** The labels of [this] list's Package representatives, keyed by the package and Profile they represent. */
fun List<Launchable>.packageRepresentativeNames(): Map<AppProfile, String> = filterIsInstance<App>()
    .filter { app -> app.isPackageRepresentative }
    .associate { app -> AppProfile(packageName = app.packageName, profile = app.profile) to app.name }
