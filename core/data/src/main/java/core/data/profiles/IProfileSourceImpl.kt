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
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import core.util.logTypeName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

class IProfileSourceImpl(context: Context) : IProfileSource {
    private val appContext: Context = context.applicationContext
    private val launcherApps: LauncherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager = appContext.getSystemService(UserManager::class.java)
    private val packageManager: PackageManager = appContext.packageManager
    private val _profiles: MutableStateFlow<List<ProfileInfo>> = MutableStateFlow(loadProfiles().orEmpty())
    override val profiles: StateFlow<List<ProfileInfo>> = _profiles.asStateFlow()
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

    init {
        runCatching { launcherApps.registerCallback(launcherCallback, Handler(Looper.getMainLooper())) }
            .onFailure { exception -> Timber.w(exception, "Could not listen for package changes") }
        ContextCompat.registerReceiver(appContext, receiver, profileIntentFilter(), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun refreshProfiles() {
        val previousUsers: Set<UserHandle> = _profiles.value.mapTo(mutableSetOf()) { profile -> profile.user }
        val profiles: List<ProfileInfo> = loadProfiles() ?: return
        _profiles.value = profiles
        val currentUsers: Set<UserHandle> = profiles.mapTo(mutableSetOf()) { profile -> profile.user }
        (currentUsers - previousUsers).forEach { user -> _changes.tryEmit(ProfileChange.ProfileAdded(user = user)) }
        (previousUsers - currentUsers).forEach { user -> _changes.tryEmit(ProfileChange.ProfileRemoved(user = user)) }
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

    override fun privateSpaceSettingsIntent(): IntentSender? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        runCatching { launcherApps.privateSpaceSettingsIntent }
            .onFailure { exception -> Timber.w(exception, "Could not read the Private Space settings") }
            .getOrNull()
    } else {
        null
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
                badgedLabel = badgedLabel(label = label, user = activity.user)
            )
        }
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
    }
}
