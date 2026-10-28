package feature.launcher

import core.data.notifications.INotificationListenerRelay
import core.data.notifications.INotificationSource
import core.data.notifications.INotificationSourceImpl
import core.data.popups.IPopupRepository
import kotlinx.coroutines.CoroutineScope
import org.robolectric.RuntimeEnvironment

/** Creates the notification source the launcher tests drive over [mockPopupRepository]. */
internal fun createTestNotificationSource(
    scope: CoroutineScope,
    mockPopupRepository: IPopupRepository,
    relay: INotificationListenerRelay,
    isAccessGranted: () -> Boolean = { true }
): INotificationSource = INotificationSourceImpl(
    context = RuntimeEnvironment.getApplication(),
    scope = scope,
    popupRepository = mockPopupRepository,
    relay = relay,
    isAccessGranted = { isAccessGranted() }
)
