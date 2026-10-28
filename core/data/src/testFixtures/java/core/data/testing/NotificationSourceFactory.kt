package core.data.testing

import android.content.Context
import core.data.notifications.INotificationListenerRelay
import core.data.notifications.INotificationSource
import core.data.notifications.INotificationSourceImpl
import core.data.popups.IPopupRepository
import kotlinx.coroutines.CoroutineScope

/** Creates the real notification source ViewModel tests drive over [mockPopupRepository]. */
fun createTestNotificationSource(
    context: Context,
    scope: CoroutineScope,
    mockPopupRepository: IPopupRepository,
    relay: INotificationListenerRelay,
    isAccessGranted: () -> Boolean = { true }
): INotificationSource = INotificationSourceImpl(
    context = context,
    scope = scope,
    popupRepository = mockPopupRepository,
    relay = relay,
    isAccessGranted = { isAccessGranted() }
)
