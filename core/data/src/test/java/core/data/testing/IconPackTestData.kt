package core.data.testing

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import core.data.icons.ICON_PACK_THEME_ACTION
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Makes Robolectric's PackageManager report [packageName] as an installed Icon pack. */
@Suppress("DEPRECATION")
fun addIconPackActivity(packageName: String, label: String) {
    val inputActivityInfo: ActivityInfo = ActivityInfo().apply {
        this.packageName = packageName
        name = "$packageName.IconPack"
        nonLocalizedLabel = label
    }
    val inputResolveInfo: ResolveInfo = ResolveInfo().apply {
        activityInfo = inputActivityInfo
    }
    shadowOf(RuntimeEnvironment.getApplication().packageManager).addResolveInfoForIntent(
        Intent(ICON_PACK_THEME_ACTION),
        inputResolveInfo
    )
}

/** Makes Robolectric's PackageManager stop reporting [packageName] as an installed Icon pack. */
@Suppress("DEPRECATION")
fun removeIconPackActivity(packageName: String) {
    shadowOf(RuntimeEnvironment.getApplication().packageManager).removeResolveInfosForIntent(
        Intent(ICON_PACK_THEME_ACTION),
        packageName
    )
}
