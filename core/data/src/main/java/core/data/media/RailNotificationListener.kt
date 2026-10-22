package core.data.media

import android.service.notification.NotificationListenerService

/**
 * Holds Rail's notification-listener binding. It never reads notifications;
 * access only grants [android.media.session.MediaSessionManager] the ability
 * to list and control active MediaSessions.
 */
class RailNotificationListener : NotificationListenerService()
