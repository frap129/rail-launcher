package core.data.shortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.os.UserManager
import core.data.apps.model.AppProfile
import core.util.logTypeName
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Reads an app's launcher shortcuts from [LauncherApps] and refreshes while collected. */
class IShortcutSourceImpl(
    context: Context,
    private val shortcutListener: IShortcutListener,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IShortcutSource {
    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager = context.getSystemService(UserManager::class.java)

    override fun shortcuts(app: AppProfile): Flow<ShortcutsResult> =
        liveFlow(apps = setOf(app), unavailable = ShortcutsResult.Unavailable) { loadShortcuts(app = app) }

    override fun resolve(keys: Set<ShortcutKey>): Flow<ShortcutResolution> {
        if (keys.isEmpty()) {
            return flowOf(ShortcutResolution())
        }
        val keysByApp: Map<AppProfile, List<ShortcutKey>> = keys.groupBy { key -> key.toAppProfile() }
        return liveFlow(apps = keysByApp.keys, unavailable = ShortcutResolution()) { loadResolution(keysByApp = keysByApp) }
    }

    override suspend fun pin(packageName: String, profile: UserHandle, ids: Set<String>) {
        withContext(dispatcher) {
            try {
                launcherApps.pinShortcuts(packageName, ids.toList(), profile)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: SecurityException) {
                logPinFailure(profile = profile, ids = ids, exception = exception)
            } catch (exception: IllegalArgumentException) {
                logPinFailure(profile = profile, ids = ids, exception = exception)
            } catch (exception: IllegalStateException) {
                logPinFailure(profile = profile, ids = ids, exception = exception)
            }
        }
    }

    /** Emits [load] now and whenever the Shortcut listener reports a change for one of [apps]. */
    private fun <T> liveFlow(apps: Collection<AppProfile>, unavailable: T, load: suspend () -> T): Flow<T> = callbackFlow {
        val refresh: Channel<Unit> = Channel(Channel.CONFLATED)
        val registration: Closeable? = shortcutListener.listen(apps = apps) { refresh.trySend(Unit) }
        if (registration == null) {
            trySend(unavailable)
            close()
            return@callbackFlow
        }
        try {
            launch {
                for (ignored: Unit in refresh) {
                    trySend(load())
                }
            }
            trySend(load())
            awaitClose()
        } finally {
            registration.close()
        }
    }

    private suspend fun loadShortcuts(app: AppProfile): ShortcutsResult = withContext(dispatcher) {
        try {
            if (!launcherApps.hasShortcutHostPermission()) {
                return@withContext ShortcutsResult.Unavailable
            }
            when (isQuietModeEnabled(profile = app.profile)) {
                true -> return@withContext ShortcutsResult.ProfilePaused
                null -> return@withContext ShortcutsResult.Unavailable
                false -> Unit
            }
            val shortcuts: List<ShortcutLaunchable> = readShortcuts(app = app)
            ShortcutsResult.Available(shortcuts = shortcuts)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: SecurityException) {
            Timber.w("No permission to read shortcuts for %s: %s", app.profile, exception.logTypeName())
            ShortcutsResult.Unavailable
        } catch (exception: IllegalStateException) {
            Timber.w("Could not read shortcuts for %s: %s", app.profile, exception.logTypeName())
            ShortcutsResult.Unavailable
        }
    }

    private suspend fun loadResolution(keysByApp: Map<AppProfile, List<ShortcutKey>>): ShortcutResolution = withContext(dispatcher) {
        if (!launcherApps.hasShortcutHostPermission()) {
            return@withContext ShortcutResolution()
        }
        val shortcuts: MutableMap<ShortcutKey, ShortcutLaunchable> = mutableMapOf()
        val goneKeys: MutableSet<ShortcutKey> = mutableSetOf()
        for ((app: AppProfile, keys: List<ShortcutKey>) in keysByApp) {
            if (isQuietModeEnabled(profile = app.profile) != false) {
                continue
            }
            val shortcutsById: Map<String, ShortcutInfo> = readResolvedShortcuts(app = app) ?: continue
            // An empty result can be a package that is mid-update, so only an App that still
            // publishes Shortcuts can vouch that a requested one is gone.
            if (shortcutsById.isEmpty()) {
                Timber.w("No shortcuts published by an App in %s; keeping requested shortcuts unresolved", app.profile)
                continue
            }
            keys.forEach { key ->
                val shortcut: ShortcutInfo? = shortcutsById[key.id]
                if (shortcut != null) {
                    shortcuts[key] = shortcut.toShortcutLaunchable(owner = app)
                } else {
                    goneKeys += key
                }
            }
        }
        ShortcutResolution(shortcuts = shortcuts, goneKeys = goneKeys)
    }

    /** Whether [profile] is in quiet mode, or null when the Profile no longer exists or is inaccessible. */
    private fun isQuietModeEnabled(profile: UserHandle): Boolean? = try {
        userManager.isQuietModeEnabled(profile)
    } catch (exception: IllegalArgumentException) {
        Timber.w("No Profile for %s: %s", profile, exception.logTypeName())
        null
    } catch (exception: SecurityException) {
        Timber.w("No access to Profile %s: %s", profile, exception.logTypeName())
        null
    }

    private fun readShortcuts(app: AppProfile): List<ShortcutLaunchable> {
        val query: LauncherApps.ShortcutQuery = LauncherApps.ShortcutQuery().apply {
            setPackage(app.packageName)
            setQueryFlags(SHORTCUT_QUERY_FLAGS)
        }
        val shortcutsById: LinkedHashMap<String, ShortcutInfo> = LinkedHashMap()
        launcherApps.getActivityList(app.packageName, app.profile).forEach { activity ->
            query.setActivity(activity.componentName)
            launcherApps.getShortcuts(query, app.profile).orEmpty().forEach { shortcut ->
                if (shortcut.isEnabled && (shortcut.isDeclaredInManifest || shortcut.isDynamic)) {
                    shortcutsById.putIfAbsent(shortcut.id, shortcut)
                }
            }
        }
        return shortcutsById.values
            .sortedWith(compareBy({ shortcut -> if (shortcut.isDeclaredInManifest) 0 else 1 }, { shortcut -> shortcut.rank }))
            .map { shortcut -> shortcut.toShortcutLaunchable(owner = app) }
    }

    private fun readResolvedShortcuts(app: AppProfile): Map<String, ShortcutInfo>? = try {
        readPackageShortcuts(app = app)
    } catch (exception: SecurityException) {
        Timber.w("No permission to resolve shortcuts for %s: %s", app.profile, exception.logTypeName())
        null
    } catch (exception: IllegalStateException) {
        Timber.w("Could not resolve shortcuts for %s: %s", app.profile, exception.logTypeName())
        null
    } catch (exception: IllegalArgumentException) {
        Timber.w("No Profile to resolve shortcuts for %s: %s", app.profile, exception.logTypeName())
        null
    }

    private fun readPackageShortcuts(app: AppProfile): Map<String, ShortcutInfo> {
        val query: LauncherApps.ShortcutQuery = LauncherApps.ShortcutQuery().apply {
            setPackage(app.packageName)
            setQueryFlags(SHORTCUT_RESOLVE_FLAGS)
        }
        return launcherApps.getShortcuts(query, app.profile).orEmpty().associateBy { shortcut -> shortcut.id }
    }

    private fun logPinFailure(profile: UserHandle, ids: Set<String>, exception: Exception) {
        Timber.w("Could not pin %d shortcuts for %s: %s", ids.size, profile, exception.logTypeName())
    }
}
