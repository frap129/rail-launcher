package core.data.apps

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.os.UserHandle

class App(info: LauncherActivityInfo, val profile: UserHandle) {
    val packageName = info.componentName.packageName
    val label = info.label.toString()

    fun getIcon(context: Context) = context.packageManager.getApplicationIcon(packageName)
    fun launch(context: Context) = context.startActivity(
        context.packageManager.getLaunchIntentForPackage(packageName)
    )
}
