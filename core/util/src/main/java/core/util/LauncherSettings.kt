package core.util

import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/** Opens the system screen for choosing the default Home app. */
fun Context.openDefaultHomeSettings() {
    tryStartActivity(Intent(Settings.ACTION_HOME_SETTINGS))
}

/** Relaunches Rail through HOME in a fresh task, then ends the current process. */
fun Context.restartRail() {
    val homeIntent: Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .setPackage(packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (tryStartActivity(homeIntent)) {
        Process.killProcess(Process.myPid())
    }
}
