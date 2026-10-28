package core.data.profiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.LauncherUserInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
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
    private val _profiles: MutableStateFlow<List<ProfileInfo>> = MutableStateFlow(loadProfiles())
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

    override fun profileFor(user: UserHandle): ProfileInfo? = profiles.value.firstOrNull { profile -> profile.user == user }

    override fun activities(user: UserHandle): List<ProfileActivity> = loadActivities(packageName = null, user = user)

    override fun activities(packageName: String, user: UserHandle): List<ProfileActivity> = loadActivities(
        packageName = packageName,
        user = user
    )

    override suspend fun requestQuiet(user: UserHandle, quiet: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                userManager.requestQuietModeEnabled(quiet, user)
            } catch (exception: SecurityException) {
                logQuietFailure(user = user, exception = exception)
            } catch (exception: IllegalArgumentException) {
                logQuietFailure(user = user, exception = exception)
            }
        }
    }

    override fun userForSerial(serial: Long): UserHandle? = runCatching {
        userManager.getUserForSerialNumber(serial)
    }.getOrNull()

    override fun serialForUser(user: UserHandle): Long? = runCatching {
        userManager.getSerialNumberForUser(user)
    }.getOrNull()?.takeIf { serial -> serial >= 0 }

    private fun loadProfiles(): List<ProfileInfo> {
        val users: List<UserHandle> = runCatching { launcherApps.profiles }
            .getOrElse { exception ->
                Timber.w(exception, "Could not read the Profiles")
                emptyList()
            }
        return users.map { user ->
            ProfileInfo(
                user = user,
                serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(-1L),
                kind = profileKind(user = user),
                isQuiet = runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false)
            )
        }
    }

    private fun reloadProfiles() {
        _profiles.value = loadProfiles()
    }

    private fun publishProfileChange(change: ProfileChange) {
        reloadProfiles()
        _changes.tryEmit(change)
    }

    private fun loadActivities(packageName: String?, user: UserHandle): List<ProfileActivity> {
        val activities: List<LauncherActivityInfo> = runCatching {
            launcherApps.getActivityList(packageName, user)
        }.getOrElse { exception ->
            Timber.w(exception, "Could not read the activities of %s", user)
            emptyList()
        }
        return activities.map { activity ->
            ProfileActivity(
                componentName = activity.componentName,
                user = activity.user,
                label = activity.label.toString()
            )
        }
    }

    private fun logQuietFailure(user: UserHandle, exception: RuntimeException) {
        Timber.w(exception, "Could not change quiet mode for %s", user)
    }

    private fun profileKind(user: UserHandle): ProfileKind = when {
        user == Process.myUserHandle() -> ProfileKind.Personal

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> launcherProfileKind(user = user)

        // Below API 35 no public API reports another Profile's managed type: UserManager's
        // isManagedProfile() only describes the process Profile, and the per-user overloads on
        // UserManager, DevicePolicyManager and CrossProfileApps are API 35 or system-only.
        // Private Space does not exist yet, so a non-personal Profile is treated as Work.
        else -> ProfileKind.Work
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun launcherProfileKind(user: UserHandle): ProfileKind {
        val userType: String? = runCatching { launcherApps.getLauncherUserInfo(user) }.getOrNull()?.userType
        return when (userType) {
            UserManager.USER_TYPE_PROFILE_MANAGED -> ProfileKind.Work
            UserManager.USER_TYPE_PROFILE_PRIVATE -> ProfileKind.Private
            else -> ProfileKind.Other
        }
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
