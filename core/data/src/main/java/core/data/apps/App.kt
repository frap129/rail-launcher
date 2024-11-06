package core.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings
import core.data.rail.RailItem

class App(info: LauncherActivityInfo, val profile: UserHandle) : RailItem() {
    val packageName = info.componentName.packageName
    private val label = info.label.toString()
    override val key = "${packageName}_$profile"

    override fun getDefaultName(): String = label
    override fun getDefaultIcon(context: Context) = context.packageManager.getApplicationIcon(packageName)
    override fun launch(context: Context) = context.startActivity(
        context.packageManager.getLaunchIntentForPackage(packageName)
    )

    fun openAppInfo(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        context.startActivity(intent)
    }
}
