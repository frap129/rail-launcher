package feature.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.util.SparseIntArray
import androidx.annotation.RequiresApi
import core.data.icons.badgedFor
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileInfo
import core.data.widgets.BindRequest
import core.data.widgets.WidgetProviderInfo
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val PROFILE_BADGE_CANVAS_DP: Int = 64

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

    /** The system-drawn badge of [profileSerial]'s Profile, or null for the personal Profile. */
    suspend fun loadProfileBadge(context: Context, profileSerial: Long, profileSource: IProfileSource): Drawable? =
        withContext(Dispatchers.IO) {
            val user: UserHandle = profileSource.userForSerial(serial = profileSerial) ?: return@withContext null
            val profile: ProfileInfo = profileSource.profileFor(user = user) ?: return@withContext null
            if (profile.kind.isPersonal) {
                return@withContext null
            }
            val canvasSize: Int = (PROFILE_BADGE_CANVAS_DP * context.resources.displayMetrics.density).roundToInt()
            val badgeCanvas: Drawable = ShapeDrawable().apply {
                setIntrinsicWidth(canvasSize)
                setIntrinsicHeight(canvasSize)
            }
            badgeCanvas.badgedFor(packageManager = context.packageManager, user = user)
                ?.toBadgeDrawable(context = context, canvasSize = canvasSize)
        }

    private fun Drawable.toBadgeDrawable(context: Context, canvasSize: Int): Drawable? {
        val bitmap: Bitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, canvasSize, canvasSize)
        draw(Canvas(bitmap))
        val visible: Rect = bitmap.findVisibleBounds() ?: return null
        return BitmapDrawable(context.resources, Bitmap.createBitmap(bitmap, visible.left, visible.top, visible.width(), visible.height()))
    }

    private fun Bitmap.findVisibleBounds(): Rect? {
        val pixels: IntArray = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        var left: Int = width
        var top: Int = height
        var right: Int = -1
        var bottom: Int = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (Color.alpha(pixels[y * width + x]) != 0) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return if (right < 0) null else Rect(left, top, right + 1, bottom + 1)
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
