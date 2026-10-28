package core.data.apps

import android.content.Context
import android.os.UserHandle
import core.data.apps.model.App
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileActivity
import core.data.profiles.ProfileChange
import core.data.profiles.ProfileInfo
import core.data.profiles.isPrivate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class IAppRepositoryImpl(
    context: Context,
    private val profileSource: IProfileSource,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IAppRepository {
    private val ownPackageName: String = context.packageName
    private val _apps: MutableStateFlow<List<App>?> = MutableStateFlow(null)
    override val apps: StateFlow<List<App>?> = _apps.asStateFlow()

    init {
        scope.launch(dispatcher) {
            profileSource.changes
                .onSubscription { _apps.value = loadApps() }
                .collect { change -> applyChange(change = change) }
        }
    }

    private fun loadApps(): List<App> = profileSource.profiles.value
        .filter { profile -> profile.isEnumerated() }
        .flatMap { profile -> profileSource.activities(profile.user).map { activity -> activity.toApp(profile = profile) } }
        .excludingOwnPackage()

    private fun applyChange(change: ProfileChange) {
        when (change) {
            is ProfileChange.PackageAdded -> replacePackage(user = change.user, packageName = change.packageName)
            is ProfileChange.PackageRemoved -> removePackage(user = change.user, packageName = change.packageName)
            is ProfileChange.PackageChanged -> replacePackage(user = change.user, packageName = change.packageName)
            is ProfileChange.ProfileAdded -> replaceProfile(user = change.user)
            is ProfileChange.ProfileRemoved -> removeProfile(user = change.user)
            is ProfileChange.ProfileAvailable, is ProfileChange.ProfileUnavailable -> refreshProfile(user = change.user)
            is ProfileChange.PackagesUnavailable -> Unit
        }
    }

    private fun replaceProfile(user: UserHandle) {
        val profile: ProfileInfo = profileSource.profileFor(user = user) ?: return
        val profileApps: List<App> = if (profile.isEnumerated()) {
            profileSource.activities(profile.user).map { activity -> activity.toApp(profile = profile) }
        } else {
            emptyList()
        }
        _apps.update { current -> (current.orEmpty().filterNot { app -> app.profile == user } + profileApps).excludingOwnPackage() }
    }

    private fun removeProfile(user: UserHandle) {
        _apps.update { current -> current?.filterNot { app -> app.profile == user } }
    }

    // A Private Space has no Apps to show while Locked; every other Profile stays listed while Paused.
    private fun refreshProfile(user: UserHandle) {
        val profile: ProfileInfo = profileSource.profileFor(user = user) ?: return
        if (profile.isPrivate) {
            replaceProfile(user = user)
        } else {
            updatePausedState(profile = profile)
        }
    }

    private fun updatePausedState(profile: ProfileInfo) {
        _apps.update { current ->
            current?.map { app ->
                if (app.profileInfo.user == profile.user) app.copy(profileInfo = app.profileInfo.copy(isQuiet = profile.isQuiet)) else app
            }
        }
    }

    private fun replacePackage(user: UserHandle, packageName: String) {
        val profile: ProfileInfo = profileSource.profileFor(user = user) ?: return
        val replacedApps: List<App> = if (!profile.isEnumerated() || packageName == ownPackageName) {
            emptyList()
        } else {
            profileSource.activities(packageName, user).map { activity -> activity.toApp(profile = profile) }
        }
        _apps.update { current ->
            current.orEmpty().filterNot { app -> app.profile == user && app.packageName == packageName } + replacedApps
        }
    }

    private fun removePackage(user: UserHandle, packageName: String) {
        _apps.update { current ->
            current?.filterNot { app -> app.profile == user && app.packageName == packageName }
        }
    }

    // Apps of a Locked Private Space are never enumerated.
    private fun ProfileInfo.isEnumerated(): Boolean = !isPrivate || !isQuiet

    private fun List<App>.excludingOwnPackage(): List<App> = filterNot { app -> app.packageName == ownPackageName }

    private fun ProfileActivity.toApp(profile: ProfileInfo): App = App(
        defaultName = label,
        profileInfo = profile,
        componentName = componentName,
        badgedLabel = badgedLabel
    )
}
