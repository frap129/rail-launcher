package feature.launcher

import androidx.compose.runtime.Immutable
import core.data.notifications.NotificationSnapshot

/** The actions a Pop-up's notification rows raise. */
@Immutable
data class PopupNotificationActions(
    val onOpen: (NotificationSnapshot) -> Unit,
    val onDismiss: (NotificationSnapshot) -> Unit,
    val onToggleExpanded: (NotificationSnapshot) -> Unit,
    val onAction: (NotificationSnapshot, Int) -> Unit,
    val onReply: (NotificationSnapshot, Int, String) -> Unit
)
