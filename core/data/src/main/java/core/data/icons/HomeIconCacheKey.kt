package core.data.icons

import android.content.ComponentName
import android.os.UserHandle
import coil3.request.Options
import coil3.size.pxOrElse
import core.data.icons.model.Icon
import core.data.icons.model.UNKNOWN_APP_UPDATE_TIME

private const val NO_REQUEST_SIZE_PX: Int = 0

/**
 * One rendered App icon, drawn for every rail surface that shows icons (Home, the App List,
 * Search and the pickers): its [identity], at [densityDpi] and [sizePx], from the App package
 * updated at [lastUpdateTime]. [componentName] names the App whose icons a package change or
 * removal evicts, and resolves the Profile of an unbadged icon.
 */
data class HomeIconCacheKey(
    val identity: String,
    val componentName: ComponentName,
    val profile: UserHandle?,
    val lastUpdateTime: Long,
    val densityDpi: Int,
    val sizePx: Int
) {
    /** The package whose stored icons a package change or removal evicts. */
    val packageName: String get() = componentName.packageName
}

/** The cache key for this icon, or null when this icon never takes part in the Home icon cache. */
internal fun Icon.toHomeIconCacheKey(options: Options): HomeIconCacheKey? {
    val (activityIcon: Icon.ActivityIcon, user: UserHandle?) = when (this) {
        is Icon.ActivityIcon -> this to null
        is Icon.ProfileIcon -> (icon as? Icon.ActivityIcon ?: return null) to profile
        else -> return null
    }
    val sizePx: Int = options.size.width.pxOrElse { NO_REQUEST_SIZE_PX }
    if (activityIcon.lastUpdateTime == UNKNOWN_APP_UPDATE_TIME || sizePx == NO_REQUEST_SIZE_PX) {
        return null
    }
    return HomeIconCacheKey(
        identity = toSource().identity,
        componentName = activityIcon.componentName,
        profile = user,
        lastUpdateTime = activityIcon.lastUpdateTime,
        densityDpi = options.context.resources.displayMetrics.densityDpi,
        sizePx = sizePx
    )
}
