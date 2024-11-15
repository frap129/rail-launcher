package core.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings
import core.data.icons.model.Icon
import core.data.launcher.model.LauncherItem

class App(override val defaultName: String, val profile: UserHandle, val componentName: ComponentName) : LauncherItem() {
    val packageName = componentName.packageName
    override val key = "${packageName}_$profile"
    override val defaultIcon = Icon.ActivityIcon(componentName)
    override var name = defaultName
    override var icon: Icon = defaultIcon

    override fun launch(context: Context) = context.startActivity(
        context.packageManager.getLaunchIntentForPackage(packageName)
    )

    fun openAppInfo(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        context.startActivity(intent)
    }
}
