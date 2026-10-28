package core.data.shortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import core.data.apps.model.App
import core.data.apps.model.AppProfile
import core.data.launchables.ILaunchableRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Reads an app's launcher shortcuts from [LauncherApps] and refreshes while collected. */
class IShortcutSourceImpl(private val context: Context, private val launchableRepository: ILaunchableRepository) : IShortcutSource {
    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager = context.getSystemService(UserManager::class.java)

    override fun shortcuts(packageName: String, profile: UserHandle): Flow<ShortcutsResult> = callbackFlow {
        val owner: AppProfile = AppProfile(packageName = packageName, profile = profile)
        val refresh: Channel<Unit> = Channel(Channel.CONFLATED)
        val callback: LauncherApps.Callback = createCallback(owners = setOf(owner), refresh = refresh)
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

    override fun appsWithShortcuts(): Flow<List<AppShortcuts>> = launchableRepository.launchableState
        .map { state -> state.launchables.filterIsInstance<App>() }
        .distinctUntilChanged()
        .flatMapLatest { apps ->
            if (apps.isEmpty()) {
                flowOf(emptyList())
            } else {
                val appFlows: List<Flow<AppShortcuts>> = apps.map { app ->
                    shortcuts(packageName = app.packageName, profile = app.profile)
                        .map { result -> AppShortcuts(app = app, result = result) }
                }
                combine(appFlows) { results -> results.toList() }
            }
        }

    override fun resolve(keys: Set<ShortcutKey>): Flow<ShortcutResolution> {
        if (keys.isEmpty()) {
            return flowOf(ShortcutResolution())
        }
        val owners: Map<AppProfile, List<ShortcutKey>> = keys.groupBy { key ->
            AppProfile(packageName = key.packageName, profile = key.profile)
        }
        return callbackFlow {
            val refresh: Channel<Unit> = Channel(Channel.CONFLATED)
            val callback: LauncherApps.Callback = createCallback(owners = owners.keys, refresh = refresh)
            if (!registerCallback(callback)) {
                trySend(ShortcutResolution())
                close()
                awaitClose { }
                return@callbackFlow
            }
            launch {
                for (ignored: Unit in refresh) {
                    trySend(loadResolution(owners = owners))
                }
            }
            trySend(loadResolution(owners = owners))
            awaitClose { launcherApps.unregisterCallback(callback) }
        }
    }

    override suspend fun pin(packageName: String, profile: UserHandle, ids: Set<String>) {
        withContext(Dispatchers.IO) {
            try {
                launcherApps.pinShortcuts(packageName, ids.toList(), profile)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: SecurityException) {
                logPinFailure(packageName = packageName, profile = profile, ids = ids, exception = exception)
            } catch (exception: IllegalArgumentException) {
                logPinFailure(packageName = packageName, profile = profile, ids = ids, exception = exception)
            } catch (exception: IllegalStateException) {
                logPinFailure(packageName = packageName, profile = profile, ids = ids, exception = exception)
            }
        }
    }

    private fun createCallback(owners: Set<AppProfile>, refresh: Channel<Unit>): LauncherApps.Callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            refreshIfMatches(owners = owners, changedPackage = packageName, changedProfile = user, refresh = refresh)
        }
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            refreshIfMatches(owners = owners, changedPackage = packageName, changedProfile = user, refresh = refresh)
        }
        override fun onPackageChanged(packageName: String, user: UserHandle) {
            refreshIfMatches(owners = owners, changedPackage = packageName, changedProfile = user, refresh = refresh)
        }
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {
            refreshIfPresent(owners = owners, changedPackages = packageNames, changedProfile = user, refresh = refresh)
        }
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {
            refreshIfPresent(owners = owners, changedPackages = packageNames, changedProfile = user, refresh = refresh)
        }
        override fun onShortcutsChanged(packageName: String, shortcuts: List<ShortcutInfo>, user: UserHandle) {
            refreshIfMatches(owners = owners, changedPackage = packageName, changedProfile = user, refresh = refresh)
        }
    }

    private fun refreshIfMatches(owners: Set<AppProfile>, changedPackage: String, changedProfile: UserHandle, refresh: Channel<Unit>) {
        if (owners.any { owner -> owner.packageName == changedPackage && owner.profile == changedProfile }) {
            refresh.trySend(Unit)
        }
    }

    private fun refreshIfPresent(
        owners: Set<AppProfile>,
        changedPackages: Array<String>,
        changedProfile: UserHandle,
        refresh: Channel<Unit>
    ) {
        if (owners.any { owner -> owner.profile == changedProfile && owner.packageName in changedPackages }) {
            refresh.trySend(Unit)
        }
    }

    private fun registerCallback(callback: LauncherApps.Callback): Boolean = runCatching {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
    }.onFailure { exception -> Timber.w(exception, "Could not listen for shortcut changes") }.isSuccess

    private suspend fun loadShortcuts(owner: AppProfile): ShortcutsResult = withContext(Dispatchers.IO) {
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

    private suspend fun loadResolution(owners: Map<AppProfile, List<ShortcutKey>>): ShortcutResolution = withContext(Dispatchers.IO) {
        if (!launcherApps.hasShortcutHostPermission()) {
            return@withContext ShortcutResolution()
        }
        val shortcuts: MutableMap<ShortcutKey, ShortcutLaunchable> = mutableMapOf()
        val goneKeys: MutableSet<ShortcutKey> = mutableSetOf()
        for ((owner: AppProfile, keys: List<ShortcutKey>) in owners) {
            if (userManager.isQuietModeEnabled(owner.profile)) {
                continue
            }
            val shortcutsById: Map<String, ShortcutInfo> = readResolvedShortcuts(owner = owner) ?: continue
            // An empty result can be a package that is mid-update, so only an App that still
            // publishes Shortcuts can vouch that a requested one is gone.
            if (shortcutsById.isEmpty()) {
                Timber.w("No shortcuts published by %s; keeping requested shortcuts unresolved", owner.packageName)
                continue
            }
            keys.forEach { key ->
                val shortcut: ShortcutInfo? = shortcutsById[key.id]
                if (shortcut != null) {
                    shortcuts[key] = shortcut.toLaunchable(owner = owner)
                } else {
                    goneKeys += key
                }
            }
        }
        ShortcutResolution(shortcuts = shortcuts, goneKeys = goneKeys)
    }

    private fun readShortcuts(owner: AppProfile): List<ShortcutLaunchable> {
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

    private fun readResolvedShortcuts(owner: AppProfile): Map<String, ShortcutInfo>? = try {
        readPackageShortcuts(owner = owner)
    } catch (exception: SecurityException) {
        Timber.w(exception, "No permission to resolve shortcuts for %s", owner.packageName)
        null
    } catch (exception: IllegalStateException) {
        Timber.w(exception, "Could not resolve shortcuts for %s", owner.packageName)
        null
    }

    private fun readPackageShortcuts(owner: AppProfile): Map<String, ShortcutInfo> {
        val query: LauncherApps.ShortcutQuery = LauncherApps.ShortcutQuery().apply {
            setPackage(owner.packageName)
            setQueryFlags(SHORTCUT_RESOLVE_FLAGS)
        }
        return launcherApps.getShortcuts(query, owner.profile).orEmpty().associateBy { shortcut -> shortcut.id }
    }

    private fun logPinFailure(packageName: String, profile: UserHandle, ids: Set<String>, exception: Exception) {
        val keys: List<String> = ids.map { id -> ShortcutKey(packageName = packageName, id = id, profile = profile).format() }
        Timber.w(exception, "Could not pin shortcuts %s", keys)
    }

    private fun ShortcutInfo.toLaunchable(owner: AppProfile): ShortcutLaunchable {
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
