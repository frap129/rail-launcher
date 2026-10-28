package feature.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.util.SparseIntArray
import androidx.annotation.RequiresApi
import core.data.profiles.IProfileSource
import core.data.widgets.BindRequest
import core.data.widgets.WidgetProviderInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android-only rendering helpers for App Widgets. */
object AppWidgetViews {
    fun bindIntent(context: Context, request: BindRequest, profileSource: IProfileSource): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, request.id.value)
            putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,
                ComponentName(request.provider.packageName, request.provider.className)
            )
            profileSource.userForSerial(request.profileSerial)?.let { user ->
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, user)
            }
        }

    suspend fun loadPreview(context: Context, info: WidgetProviderInfo, profileSource: IProfileSource): Drawable? =
        withContext(Dispatchers.IO) {
            val user: UserHandle = profileSource.userForSerial(info.profileSerial) ?: Process.myUserHandle()
            val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
            val platformInfo: AppWidgetProviderInfo? = runCatching {
                manager.getInstalledProvidersForProfile(user)
                    .firstOrNull { provider ->
                        provider.provider.packageName == info.provider.packageName &&
                            provider.provider.className == info.provider.className
                    }
            }.getOrNull()
            runCatching {
                val resourceId: Int = when {
                    platformInfo != null && platformInfo.previewImage != 0 -> platformInfo.previewImage
                    platformInfo != null && platformInfo.icon != 0 -> platformInfo.icon
                    else -> 0
                }
                if (resourceId == 0) {
                    null
                } else {
                    val launcherApps = context.getSystemService(LauncherApps::class.java)
                    val appInfo = launcherApps.getApplicationInfo(info.provider.packageName, 0, user)
                    context.packageManager.getResourcesForApplication(appInfo).getDrawable(resourceId, null)
                }
            }.getOrNull()
        }

    @RequiresApi(Build.VERSION_CODES.S)
    fun colorResources(context: Context): SparseIntArray = SparseIntArray().apply {
        systemPaletteIds.forEach { resourceId ->
            append(resourceId, context.getColor(resourceId))
        }
    }

    private val systemPaletteIds: IntArray = intArrayOf(
        android.R.color.system_accent1_0,
        android.R.color.system_accent1_10,
        android.R.color.system_accent1_50,
        android.R.color.system_accent1_100,
        android.R.color.system_accent1_200,
        android.R.color.system_accent1_300,
        android.R.color.system_accent1_400,
        android.R.color.system_accent1_500,
        android.R.color.system_accent1_600,
        android.R.color.system_accent1_700,
        android.R.color.system_accent1_800,
        android.R.color.system_accent1_900,
        android.R.color.system_accent1_1000,
        android.R.color.system_accent2_0,
        android.R.color.system_accent2_10,
        android.R.color.system_accent2_50,
        android.R.color.system_accent2_100,
        android.R.color.system_accent2_200,
        android.R.color.system_accent2_300,
        android.R.color.system_accent2_400,
        android.R.color.system_accent2_500,
        android.R.color.system_accent2_600,
        android.R.color.system_accent2_700,
        android.R.color.system_accent2_800,
        android.R.color.system_accent2_900,
        android.R.color.system_accent2_1000,
        android.R.color.system_accent3_0,
        android.R.color.system_accent3_10,
        android.R.color.system_accent3_50,
        android.R.color.system_accent3_100,
        android.R.color.system_accent3_200,
        android.R.color.system_accent3_300,
        android.R.color.system_accent3_400,
        android.R.color.system_accent3_500,
        android.R.color.system_accent3_600,
        android.R.color.system_accent3_700,
        android.R.color.system_accent3_800,
        android.R.color.system_accent3_900,
        android.R.color.system_accent3_1000,
        android.R.color.system_neutral1_0,
        android.R.color.system_neutral1_10,
        android.R.color.system_neutral1_50,
        android.R.color.system_neutral1_100,
        android.R.color.system_neutral1_200,
        android.R.color.system_neutral1_300,
        android.R.color.system_neutral1_400,
        android.R.color.system_neutral1_500,
        android.R.color.system_neutral1_600,
        android.R.color.system_neutral1_700,
        android.R.color.system_neutral1_800,
        android.R.color.system_neutral1_900,
        android.R.color.system_neutral1_1000,
        android.R.color.system_neutral2_0,
        android.R.color.system_neutral2_10,
        android.R.color.system_neutral2_50,
        android.R.color.system_neutral2_100,
        android.R.color.system_neutral2_200,
        android.R.color.system_neutral2_300,
        android.R.color.system_neutral2_400,
        android.R.color.system_neutral2_500,
        android.R.color.system_neutral2_600,
        android.R.color.system_neutral2_700,
        android.R.color.system_neutral2_800,
        android.R.color.system_neutral2_900,
        android.R.color.system_neutral2_1000
    )
}
