package core.data.profiles

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.LauncherUserInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import core.data.retryDelayMillis
import core.util.logTypeName
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class IProfileSourceImpl(
    context: Context,
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) : IProfileSource {
    private val appContext: Context = context.applicationContext
    private val launcherApps: LauncherApps by lazy { appContext.getSystemService(LauncherApps::class.java) }
    private val userManager: UserManager by lazy { appContext.getSystemService(UserManager::class.java) }
    private val packageManager: PackageManager by lazy { appContext.packageManager }
    private val _profiles: MutableStateFlow<List<ProfileInfo>> = MutableStateFlow(emptyList())
    override val profiles: StateFlow<List<ProfileInfo>> = _profiles.asStateFlow()
    private val _isLoaded: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()
    private var loadedAtElapsedRealtime: Long = 0L
    private val _changes: MutableSharedFlow<ProfileChange> = MutableSharedFlow(
        extraBufferCapacity = PROFILE_CHANGE_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val changes: SharedFlow<ProfileChange> = _changes.asSharedFlow()
    private val launcherCallback: LauncherApps.Callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            _changes.tryEmit(ProfileChange.PackageAdded(user = user, packageName = packageName))
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            _changes.tryEmit(ProfileChange.PackageRemoved(user = user, packageName = packageName))
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            _changes.tryEmit(ProfileChange.PackageChanged(user = user, packageName = packageName))
        }

        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = Unit

        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {
            _changes.tryEmit(ProfileChange.PackagesUnavailable(user = user, packageNames = packageNames.toList(), replacing = replacing))
        }

        override fun onPackagesSuspended(packageNames: Array<String>, user: UserHandle) {
            _changes.tryEmit(ProfileChange.PackagesSuspended(user = user, packageNames = packageNames.toList(), isSuspended = true))
        }

        override fun onPackagesUnsuspended(packageNames: Array<String>, user: UserHandle) {
            _changes.tryEmit(ProfileChange.PackagesSuspended(user = user, packageNames = packageNames.toList(), isSuspended = false))
        }

        override fun onUserConfigChanged(userInfo: LauncherUserInfo) {
            reloadProfiles()
        }
    }
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val user: UserHandle = intent.userExtra() ?: return
            val change: ProfileChange = when (intent.action) {
                Intent.ACTION_PROFILE_ADDED, Intent.ACTION_MANAGED_PROFILE_ADDED -> ProfileChange.ProfileAdded(user = user)

                Intent.ACTION_PROFILE_REMOVED, Intent.ACTION_MANAGED_PROFILE_REMOVED -> ProfileChange.ProfileRemoved(user = user)

                Intent.ACTION_PROFILE_AVAILABLE, Intent.ACTION_MANAGED_PROFILE_AVAILABLE -> ProfileChange.ProfileAvailable(user = user)

                Intent.ACTION_PROFILE_UNAVAILABLE, Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE -> ProfileChange.ProfileUnavailable(
                    user = user
                )

                else -> return
            }
            publishProfileChange(change = change)
        }
    }

    private val initialLoad: Job = scope.launch(dispatcher) {
        runCatching { launcherApps.registerCallback(launcherCallback, Handler(Looper.getMainLooper())) }
            .onFailure { exception -> Timber.w(exception, "Could not listen for package changes") }
        runCatching {
            ContextCompat.registerReceiver(appContext, receiver, profileIntentFilter(), ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onFailure { exception -> Timber.w(exception, "Could not listen for Profile changes") }
        _profiles.value = loadInitialProfiles()
        loadedAtElapsedRealtime = elapsedRealtime()
        _isLoaded.value = true
    }

    override suspend fun refreshProfiles() {
        initialLoad.join()
        if (elapsedRealtime() - loadedAtElapsedRealtime <= STARTUP_RESUME_WINDOW_MS) {
            return
        }
        withContext(dispatcher) {
            val previousProfiles: List<ProfileInfo> = _profiles.value
            val profiles: List<ProfileInfo> = loadProfiles() ?: return@withContext
            if (!_profiles.compareAndSet(expect = previousProfiles, update = profiles)) {
                return@withContext
            }
            publishRefreshedProfiles(previous = previousProfiles, current = profiles)
        }
    }

    override fun profileFor(user: UserHandle): ProfileInfo? = profiles.value.firstOrNull { profile -> profile.user == user }

    override fun kindFor(user: UserHandle): ProfileKind {
        val profile: ProfileInfo? = profileFor(user = user)
        if (profile == null) {
            Timber.w("No Profile for %s", user)
            return ProfileKind.Other
        }
        return profile.kind
    }

    override fun badgedLabel(label: String, user: UserHandle): String = runCatching {
        packageManager.getUserBadgedLabel(label, user).toString()
    }.onFailure { exception ->
        Timber.w("Could not read a badged label for %s: %s", user, exception.logTypeName())
    }.getOrDefault(label)

    override fun activities(user: UserHandle): List<ProfileActivity> = loadActivities(packageName = null, user = user)

    override fun activities(packageName: String, user: UserHandle): List<ProfileActivity> = loadActivities(
        packageName = packageName,
        user = user
    )

    override fun activityIcon(componentName: ComponentName, user: UserHandle): Drawable? {
        val activity: LauncherActivityInfo? = launcherActivities(packageName = componentName.packageName, user = user)
            .onFailure { Timber.w("Could not read an activity icon for %s", user) }
            .getOrNull()
            ?.firstOrNull { launcherActivity -> launcherActivity.componentName == componentName }
        if (activity == null) {
            Timber.w("Could not find an activity icon for %s", user)
            return null
        }
        return runCatching {
            activity.getIcon(appContext.resources.displayMetrics.densityDpi)
        }.onFailure { Timber.w("Could not load an activity icon for %s", user) }
            .getOrNull()
    }

    override fun applicationIcon(packageName: String, user: UserHandle): Drawable? = runCatching {
        val applicationInfo: ApplicationInfo = launcherApps.getApplicationInfo(packageName, 0, user)
        packageManager.getApplicationIcon(applicationInfo)
    }.getOrElse {
        Timber.w("Could not load an application icon for %s", user)
        null
    }

    override suspend fun requestQuiet(user: UserHandle, quiet: Boolean): QuietRequestResult = withContext(Dispatchers.IO) {
        try {
            if (userManager.requestQuietModeEnabled(quiet, user)) {
                QuietRequestResult.Changed
            } else if (!quiet) {
                // The system shows its credential screen instead of leaving quiet mode.
                QuietRequestResult.AwaitingCredential
            } else {
                QuietRequestResult.Failed
            }
        } catch (exception: SecurityException) {
            logQuietFailure(user = user, exception = exception)
            QuietRequestResult.Failed
        } catch (exception: IllegalArgumentException) {
            logQuietFailure(user = user, exception = exception)
            QuietRequestResult.Failed
        }
    }

    override fun userForSerial(serial: Long): UserHandle? = runCatching {
        userManager.getUserForSerialNumber(serial)
    }.getOrNull()

    override fun serialForUser(user: UserHandle): Long? = runCatching {
        userManager.getSerialNumberForUser(user)
    }.getOrNull()?.takeIf { serial -> serial >= 0 }

    override suspend fun privateSpaceSettingsIntent(): IntentSender? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            runCatching { launcherApps.privateSpaceSettingsIntent }
                .onFailure { exception -> Timber.w(exception, "Could not read the Private Space settings") }
                .getOrNull()
        } else {
            null
        }
    }

    override fun canUninstall(user: UserHandle): Boolean = runCatching {
        val restrictions: Bundle = userManager.getUserRestrictions(user)
        !restrictions.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS, false) &&
            !restrictions.getBoolean(UserManager.DISALLOW_APPS_CONTROL, false)
    }.onFailure { exception ->
        Timber.w("Could not read uninstall restrictions for %s: %s", user, exception.logTypeName())
    }.getOrDefault(false)

    /**
     * Reads the Profiles until the system answers, backing off as long-lived flows do. It never
     * publishes an empty list instead: App List readiness without Apps would let the stale-key
     * cleanups delete every stored Favorite and customization.
     */
    private suspend fun loadInitialProfiles(): List<ProfileInfo> {
        var attempt: Long = 0L
        var profiles: List<ProfileInfo>? = loadProfiles()
        while (profiles == null) {
            val delayMs: Long = retryDelayMillis(attempt = attempt)
            Timber.w("Initial Profile load failed %d times; retrying in %d ms", attempt + 1, delayMs)
            delay(delayMs)
            attempt++
            profiles = loadProfiles()
        }
        return profiles
    }

    private fun loadProfiles(): List<ProfileInfo>? {
        val users: List<UserHandle> = runCatching { launcherApps.profiles }
            .onFailure { exception -> Timber.w(exception, "Could not read the Profiles") }
            .getOrNull() ?: return null
        return users.map { user ->
            val launcherUserInfo: LauncherUserInfo? = launcherUserInfo(user = user)
            ProfileInfo(
                user = user,
                serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(-1L),
                kind = profileKind(user = user, launcherUserInfo = launcherUserInfo),
                isQuiet = runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false),
                isEntrypointHidden = launcherUserInfo.isEntrypointHidden()
            )
        }
    }

    private fun reloadProfiles() {
        val profiles: List<ProfileInfo> = loadProfiles() ?: return
        _profiles.value = profiles
    }

    private fun publishRefreshedProfiles(previous: List<ProfileInfo>, current: List<ProfileInfo>) {
        val previousByUser: Map<UserHandle, ProfileInfo> = previous.associateBy { profile -> profile.user }
        val currentByUser: Map<UserHandle, ProfileInfo> = current.associateBy { profile -> profile.user }
        (currentByUser.keys - previousByUser.keys).forEach { user -> _changes.tryEmit(ProfileChange.ProfileAdded(user = user)) }
        (previousByUser.keys - currentByUser.keys).forEach { user -> _changes.tryEmit(ProfileChange.ProfileRemoved(user = user)) }
        (currentByUser.keys intersect previousByUser.keys).forEach { user ->
            val previousProfile: ProfileInfo = previousByUser.getValue(user)
            val currentProfile: ProfileInfo = currentByUser.getValue(user)
            availabilityChange(previous = previousProfile, current = currentProfile)?.let { change -> _changes.tryEmit(change) }
        }
    }

    private fun availabilityChange(previous: ProfileInfo, current: ProfileInfo): ProfileChange? = when {
        previous.isQuiet == current.isQuiet -> null
        current.isQuiet -> ProfileChange.ProfileUnavailable(user = current.user)
        else -> ProfileChange.ProfileAvailable(user = current.user)
    }

    private fun publishProfileChange(change: ProfileChange) {
        reloadProfiles()
        _changes.tryEmit(change)
    }

    private fun launcherActivities(packageName: String?, user: UserHandle): Result<List<LauncherActivityInfo>> =
        runCatching { launcherApps.getActivityList(packageName, user) }

    private fun loadActivities(packageName: String?, user: UserHandle): List<ProfileActivity> {
        val activities: List<LauncherActivityInfo> = launcherActivities(packageName = packageName, user = user)
            .onFailure { exception -> Timber.w("Could not read the activities of %s: %s", user, exception.logTypeName()) }
            .getOrDefault(emptyList())
        return activities.map { activity ->
            val label: String = activity.label.toString()
            ProfileActivity(
                componentName = activity.componentName,
                user = activity.user,
                label = label,
                badgedLabel = badgedLabel(label = label, user = activity.user),
                firstInstallTime = activity.firstInstallTime,
                lastUpdateTime = updateTimeFor(activity = activity),
                isRemovable = activity.applicationInfo.isRemovable(),
                isSystemApp = activity.applicationInfo.isSystemApp(),
                isSuspended = activity.applicationInfo.isSuspended()
            )
        }
    }

    private fun updateTimeFor(activity: LauncherActivityInfo): Long {
        val sourceDir: String = activity.applicationInfo.sourceDir ?: run {
            Timber.w("No APK path for %s", activity.componentName)
            return 0L
        }
        val lastModified: Long = runCatching { File(sourceDir).lastModified() }
            .onFailure { exception -> Timber.w(exception, "Could not read the update time of %s", activity.componentName) }
            .getOrDefault(0L)
        if (lastModified == 0L) {
            Timber.w("No update time for %s", activity.componentName)
        }
        return lastModified
    }

    private fun logQuietFailure(user: UserHandle, exception: RuntimeException) {
        Timber.w(exception, "Could not change quiet mode for %s", user)
    }

    private fun profileKind(user: UserHandle, launcherUserInfo: LauncherUserInfo?): ProfileKind = when {
        user == Process.myUserHandle() -> ProfileKind.Personal

        // Below API 35 no public API reports another Profile's managed type: UserManager's
        // isManagedProfile() only describes the process Profile, and the per-user overloads on
        // UserManager, DevicePolicyManager and CrossProfileApps are API 35 or system-only.
        // Private Space does not exist yet, so a non-personal Profile is treated as Work.
        Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM -> ProfileKind.Work

        else -> when (launcherUserInfo?.userType) {
            UserManager.USER_TYPE_PROFILE_MANAGED -> ProfileKind.Work
            UserManager.USER_TYPE_PROFILE_PRIVATE -> ProfileKind.Private
            else -> ProfileKind.Other
        }
    }

    private fun launcherUserInfo(user: UserHandle): LauncherUserInfo? = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    ) {
        runCatching { launcherApps.getLauncherUserInfo(user) }.getOrNull()
    } else {
        null
    }

    private fun LauncherUserInfo?.isEntrypointHidden(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            return false
        }
        return runCatching {
            this?.userConfig?.getBoolean(LauncherUserInfo.PRIVATE_SPACE_ENTRYPOINT_HIDDEN, false) ?: false
        }.getOrDefault(false)
    }

    private fun profileIntentFilter(): IntentFilter = IntentFilter().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            addAction(Intent.ACTION_PROFILE_ADDED)
            addAction(Intent.ACTION_PROFILE_REMOVED)
            addAction(Intent.ACTION_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
        } else {
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.userExtra(): UserHandle? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_USER)
    }

    private companion object {
        const val PROFILE_CHANGE_BUFFER: Int = 64

        /** The first activity resume follows the initial load within this window on a cold start. */
        const val STARTUP_RESUME_WINDOW_MS: Long = 2_000L
    }
}

private fun ApplicationInfo.isSystemApp(): Boolean = flags and ApplicationInfo.FLAG_SYSTEM != 0

// An updated System App can be uninstalled back to its factory version; other System Apps cannot be removed.
private fun ApplicationInfo.isRemovable(): Boolean = !isSystemApp() || flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0

private fun ApplicationInfo.isSuspended(): Boolean = flags and ApplicationInfo.FLAG_SUSPENDED != 0
