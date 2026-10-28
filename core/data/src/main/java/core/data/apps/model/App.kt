package core.data.apps.model

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableAppearance
import core.util.openApplicationDetailsSettings

data class App(
    override val defaultName: String,
    val profile: UserHandle,
    val componentName: ComponentName,
    override val name: String = defaultName,
    override val defaultIcon: Icon = Icon.ActivityIcon(componentName),
    override val icon: Icon = defaultIcon
) : Launchable() {
    val packageName: String = componentName.packageName
    override val key: String = "${packageName}_$profile"

    override fun withCustomization(appearance: LaunchableAppearance): App = copy(name = appearance.name, icon = appearance.icon)

    override fun launch(context: Context) {
        val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
        launcherApps.startMainActivity(componentName, profile, null, null)
    }

    fun openAppInfo(context: Context) {
        context.openApplicationDetailsSettings(componentName = componentName, profile = profile)
    }
}
