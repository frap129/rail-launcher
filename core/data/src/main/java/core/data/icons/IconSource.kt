package core.data.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import androidx.core.content.ContextCompat
import core.data.icons.model.Icon
import core.data.profiles.IProfileSource
import core.data.shortcuts.SHORTCUT_RESOLVE_FLAGS
import core.data.shortcuts.ShortcutKey

internal sealed interface IconSource {
    val cacheKey: String

    fun loadDrawable(context: Context): Drawable?
}

private class RailIconSource(private val resId: Int) : IconSource {
    override val cacheKey: String = "rail:$resId"

    override fun loadDrawable(context: Context): Drawable? = ContextCompat.getDrawable(context, resId)
}

private class ActivityIconSource(
    private val componentName: ComponentName,
    private val profile: UserHandle?,
    private val profileSource: IProfileSource?
) : IconSource {
    override val cacheKey: String = "activity:${componentName.flattenToString()}"

    override fun loadDrawable(context: Context): Drawable? = if (profile != null && profileSource != null) {
        profileSource.activityIcon(componentName = componentName, user = profile)
    } else {
        context.packageManager.getActivityIcon(componentName)
    }
}

private class ApplicationIconSource(
    private val packageName: String,
    private val profile: UserHandle?,
    private val profileSource: IProfileSource?
) : IconSource {
    override val cacheKey: String = "application:$packageName"

    override fun loadDrawable(context: Context): Drawable? = if (profile != null && profileSource != null) {
        profileSource.applicationIcon(packageName = packageName, user = profile)
    } else {
        context.packageManager.getApplicationIcon(packageName)
    }
}

private class PackIconSource(private val componentName: ComponentName, private val packPackageName: String, private val resId: Int) :
    IconSource {
    override val cacheKey: String = "pack:$packPackageName:${componentName.flattenToString()}:$resId"

    override fun loadDrawable(context: Context): Drawable? =
        context.packageManager.getResourcesForApplication(packPackageName).getDrawable(resId, null)
}

private class FolderIconSource(private val shade: Int) : IconSource {
    override val cacheKey: String = "folder:$shade"

    override fun loadDrawable(context: Context): Drawable = FolderIconDrawable(context = context, shade = shade)
}

private class ProfileIconSource(private val icon: Icon, private val profile: UserHandle, profileSource: IProfileSource?) : IconSource {
    private val source: IconSource = icon.toSource(profileSource = profileSource, profile = profile)
    override val cacheKey: String = "${source.cacheKey}:profile:$profile"

    override fun loadDrawable(context: Context): Drawable? {
        val iconDrawable: Drawable = source.loadDrawable(context) ?: return null
        return iconDrawable.badgedFor(packageManager = context.packageManager, user = profile) ?: iconDrawable
    }
}

private class ShortcutIconSource(private val packageName: String, private val shortcutId: String, private val profile: UserHandle) :
    IconSource {
    override val cacheKey: String = ShortcutKey(packageName = packageName, id = shortcutId, profile = profile).format()

    override fun loadDrawable(context: Context): Drawable? {
        val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
        val shortcutIcon: Drawable? = runCatching {
            val shortcutInfo: ShortcutInfo? = launcherApps.getShortcuts(
                LauncherApps.ShortcutQuery().apply {
                    setPackage(packageName)
                    setQueryFlags(SHORTCUT_RESOLVE_FLAGS)
                    setShortcutIds(listOf(shortcutId))
                },
                profile
            )?.firstOrNull { shortcut -> shortcut.id == shortcutId }
            shortcutInfo?.let { info -> launcherApps.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi) }
        }.getOrNull()
        if (shortcutIcon != null) {
            return shortcutIcon
        }
        return runCatching {
            launcherApps.getApplicationInfo(packageName, 0, profile).let { appInfo ->
                context.packageManager.getApplicationIcon(appInfo)
            }
        }.getOrNull()
    }
}

internal fun Icon.toSource(profileSource: IProfileSource? = null, profile: UserHandle? = null): IconSource = when (this) {
    is Icon.ActivityIcon -> ActivityIconSource(componentName = componentName, profile = profile, profileSource = profileSource)
    is Icon.ApplicationIcon -> ApplicationIconSource(packageName = packageName, profile = profile, profileSource = profileSource)
    is Icon.FolderIcon -> FolderIconSource(shade)
    is Icon.PackIcon -> PackIconSource(componentName, packPackageName, resId)
    is Icon.ProfileIcon -> ProfileIconSource(icon = icon, profile = this.profile, profileSource = profileSource)
    is Icon.RailIcon -> RailIconSource(resId)
    is Icon.ShortcutIcon -> ShortcutIconSource(packageName = packageName, shortcutId = shortcutId, profile = this.profile)
}
