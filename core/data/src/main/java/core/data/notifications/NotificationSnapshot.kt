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
    val expandedBody: String,
    val actions: List<NotificationAction>,
    val isClearable: Boolean
) {
    /** The App this notification belongs to. */
    val owner: AppProfile get() = AppProfile(packageName = packageName, profile = profile)

    /** Whether expanding reveals a body or actions beyond the collapsed title and one body line. */
    val isExpandable: Boolean get() = expandedBody.isNotBlank() || actions.isNotEmpty()
}
