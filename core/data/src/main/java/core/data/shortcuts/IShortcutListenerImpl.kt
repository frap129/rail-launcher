package core.data.shortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import core.data.apps.model.AppProfile
import java.io.Closeable
import timber.log.Timber

/**
 * Keeps one [LauncherApps] callback registered while anything listens, routing Shortcut and
 * package changes only to the Apps each listener registered for.
 *
 * Listeners come and go on any thread while the callback delivers on the main looper, so [lock]
 * serializes listening, stopping and routing: the callback is registered while a listener exists,
 * is unregistered exactly once when the last stops, and never reaches a listener after it stopped.
 */
class IShortcutListenerImpl(context: Context) : IShortcutListener {
    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
    private val lock: Any = Any()
    private val registrations: MutableList<ListenerRegistration> = mutableListOf()
    private var callback: LauncherApps.Callback? = null

    override fun listen(apps: Collection<AppProfile>, onChange: () -> Unit): Closeable? {
        val registration: ListenerRegistration = ListenerRegistration(apps = apps, onChange = onChange)
        synchronized(lock) {
            if (callback == null) {
                val registered: LauncherApps.Callback = createCallback()
                if (!registerCallback(callback = registered)) {
                    return null
                }
                callback = registered
            }
            registrations += registration
        }
        return Closeable { stopListening(registration = registration) }
    }

    private fun stopListening(registration: ListenerRegistration) {
        synchronized(lock) {
            if (!registrations.remove(registration)) {
                return
            }
            if (registrations.isNotEmpty()) {
                return
            }
            val registered: LauncherApps.Callback = callback ?: return
            try {
                launcherApps.unregisterCallback(registered)
            } catch (exception: RuntimeException) {
                Timber.w(exception, "Could not stop listening for shortcut changes")
            } finally {
                callback = null
            }
        }
    }

    private fun createCallback(): LauncherApps.Callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            dispatchChange(changed = changedApps(packageNames = listOf(packageName), profile = user))
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            dispatchChange(changed = changedApps(packageNames = listOf(packageName), profile = user))
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            dispatchChange(changed = changedApps(packageNames = listOf(packageName), profile = user))
        }

        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {
            dispatchChange(changed = changedApps(packageNames = packageNames.toList(), profile = user))
        }

        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {
            dispatchChange(changed = changedApps(packageNames = packageNames.toList(), profile = user))
        }

        override fun onShortcutsChanged(packageName: String, shortcuts: List<ShortcutInfo>, user: UserHandle) {
            dispatchChange(changed = changedApps(packageNames = listOf(packageName), profile = user))
        }
    }

    private fun changedApps(packageNames: Collection<String>, profile: UserHandle): Set<AppProfile> =
        packageNames.mapTo(mutableSetOf()) { packageName -> AppProfile(packageName = packageName, profile = profile) }

    private fun dispatchChange(changed: Set<AppProfile>) {
        synchronized(lock) {
            registrations.forEach { registration ->
                if (registration.listensFor(changed = changed)) {
                    registration.onChange()
                }
            }
        }
    }

    private fun registerCallback(callback: LauncherApps.Callback): Boolean = runCatching {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
    }.onFailure { exception -> Timber.w(exception, "Could not listen for shortcut changes") }.isSuccess

    private class ListenerRegistration(private val apps: Collection<AppProfile>, val onChange: () -> Unit) {
        fun listensFor(changed: Set<AppProfile>): Boolean = apps.any { app -> app in changed }
    }
}
