package core.data.shortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Reads an app's launcher shortcuts from [LauncherApps] and refreshes while collected. */
class IShortcutSourceImpl(private val context: Context) : IShortcutSource {
    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager = context.getSystemService(UserManager::class.java)

    override fun shortcuts(packageName: String, profile: UserHandle): Flow<ShortcutsResult> = callbackFlow {
        val owner: ShortcutOwner = ShortcutOwner(packageName = packageName, profile = profile)
        val refresh: Channel<Unit> = Channel(Channel.CONFLATED)
        val callback: LauncherApps.Callback = createCallback(owner = owner, refresh = refresh)
        if (!registerCallback(callback)) {
            trySend(ShortcutsResult.Unavailable)
            close()
            awaitClose { }
            return@callbackFlow
        }
        launch {
            for (ignored: Unit in refresh) {
                trySend(loadShortcuts(owner = owner))
            }
        }
        trySend(loadShortcuts(owner = owner))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    private fun createCallback(owner: ShortcutOwner, refresh: Channel<Unit>): LauncherApps.Callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            refreshOwnerIfMatches(owner = owner, changedPackage = packageName, changedProfile = user, refresh = refresh)
        }
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            refreshOwnerIfMatches(owner = owner, changedPackage = packageName, changedProfile = user, refresh = refresh)
        }
        override fun onPackageChanged(packageName: String, user: UserHandle) {
            refreshOwnerIfMatches(owner = owner, changedPackage = packageName, changedProfile = user, refresh = refresh)
        }
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {
            refreshOwnerIfPresent(owner = owner, changedPackages = packageNames, changedProfile = user, refresh = refresh)
        }
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {
            refreshOwnerIfPresent(owner = owner, changedPackages = packageNames, changedProfile = user, refresh = refresh)
        }
        override fun onShortcutsChanged(packageName: String, shortcuts: List<ShortcutInfo>, user: UserHandle) {
            refreshOwnerIfMatches(owner = owner, changedPackage = packageName, changedProfile = user, refresh = refresh)
        }
    }

    private fun refreshOwnerIfMatches(owner: ShortcutOwner, changedPackage: String, changedProfile: UserHandle, refresh: Channel<Unit>) {
        if (owner.packageName == changedPackage && owner.profile == changedProfile) {
            refresh.trySend(Unit)
        }
    }

    private fun refreshOwnerIfPresent(
        owner: ShortcutOwner,
        changedPackages: Array<String>,
        changedProfile: UserHandle,
        refresh: Channel<Unit>
    ) {
        if (owner.profile == changedProfile && owner.packageName in changedPackages) {
            refresh.trySend(Unit)
        }
    }

    private fun registerCallback(callback: LauncherApps.Callback): Boolean = runCatching {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
    }.onFailure { exception -> Timber.w(exception, "Could not listen for shortcut changes") }.isSuccess

    private suspend fun loadShortcuts(owner: ShortcutOwner): ShortcutsResult = withContext(Dispatchers.IO) {
        try {
            if (!launcherApps.hasShortcutHostPermission()) {
                return@withContext ShortcutsResult.Unavailable
            }
            if (userManager.isQuietModeEnabled(owner.profile)) {
                return@withContext ShortcutsResult.ProfilePaused
            }
            val shortcuts: List<ShortcutLaunchable> = readShortcuts(owner = owner)
            ShortcutsResult.Available(shortcuts = shortcuts)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: SecurityException) {
            Timber.w(exception, "No permission to read shortcuts for %s", owner.packageName)
            ShortcutsResult.Unavailable
        } catch (exception: IllegalStateException) {
            Timber.w(exception, "Could not read shortcuts for %s", owner.packageName)
            ShortcutsResult.Unavailable
        }
    }

    private fun readShortcuts(owner: ShortcutOwner): List<ShortcutLaunchable> {
        val query: LauncherApps.ShortcutQuery = LauncherApps.ShortcutQuery().apply {
            setPackage(owner.packageName)
            setQueryFlags(SHORTCUT_QUERY_FLAGS)
        }
        val shortcutsById: LinkedHashMap<String, ShortcutInfo> = LinkedHashMap()
        launcherApps.getActivityList(owner.packageName, owner.profile).forEach { activity ->
            query.setActivity(activity.componentName)
            launcherApps.getShortcuts(query, owner.profile).orEmpty().forEach { shortcut ->
                if (shortcut.isEnabled && (shortcut.isDeclaredInManifest || shortcut.isDynamic)) {
                    shortcutsById.putIfAbsent(shortcut.id, shortcut)
                }
            }
        }
        return shortcutsById.values
            .sortedWith(compareBy({ shortcut -> if (shortcut.isDeclaredInManifest) 0 else 1 }, { shortcut -> shortcut.rank }))
            .map { shortcut -> shortcut.toLaunchable(owner = owner) }
    }

    private fun ShortcutInfo.toLaunchable(owner: ShortcutOwner): ShortcutLaunchable {
        val shortLabel: String = shortLabel?.toString()?.takeIf { label -> label.isNotBlank() } ?: owner.packageName
        val longLabel: String = longLabel?.toString()?.takeIf { label -> label.isNotBlank() } ?: shortLabel
        return ShortcutLaunchable(
            packageName = owner.packageName,
            shortcutId = id,
            profile = owner.profile,
            shortLabel = shortLabel,
            longLabel = longLabel,
            isEnabled = isEnabled
        )
    }
}

private data class ShortcutOwner(val packageName: String, val profile: UserHandle)
