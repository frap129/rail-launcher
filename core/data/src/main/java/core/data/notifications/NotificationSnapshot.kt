package core.data.notifications

import android.os.UserHandle
import core.data.apps.model.AppProfile

/** One active notification Rail can show in a Pop-up, without its content intent. */
data class NotificationSnapshot(
    val key: String,
    val packageName: String,
    val profile: UserHandle,
    val postTime: Long,
    val title: String,
    val body: String,
    val isClearable: Boolean,
    val hasBigText: Boolean,
    val hasActions: Boolean
) {
    /** The App this notification belongs to. */
    val owner: AppProfile get() = AppProfile(packageName = packageName, profile = profile)
}
