package core.data.apps

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.os.UserHandle
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
}
