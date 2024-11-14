package core.data.apps

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings
import core.data.launcher.model.LauncherItem

class App(override val defaultName: String, override val defaultIcon: Drawable, val profile: UserHandle, val packageName: String) :
    LauncherItem() {
    override val key = "${packageName}_$profile"
    override var name = defaultName
    override var icon = defaultIcon

    override fun launch(context: Context) = context.startActivity(
        context.packageManager.getLaunchIntentForPackage(packageName)
    )

    fun openAppInfo(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        context.startActivity(intent)
    }
}
