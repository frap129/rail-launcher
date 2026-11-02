package core.data.apps

import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import core.data.apps.model.App
import core.data.profiles.IPrivateSpaceStatus
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
    private val privateSpaceStatus: IPrivateSpaceStatus,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IAppRepository {
    private val ownPackageName: String = context.packageName
    private val _apps: MutableStateFlow<List<App>?> = MutableStateFlow(null)
    override val apps: StateFlow<List<App>?> = _apps.asStateFlow()

    init {
        scope.launch(dispatcher) {
            profileSource.changes
                .onSubscription { publishAllProfiles() }
                .collect { change -> applyChange(change = change) }
        }
    }

    private fun publishAllProfiles() {
        val profiles: List<ProfileInfo> = profileSource.profiles.value.filter { profile ->
            privateSpaceStatus.canListApps(profile = profile)
        }
        val apps: List<App> = profiles.flatMap { profile ->
            profileSource.activities(profile.user).toApps(profile = profile)
        }
        publish(
            apps = apps.excludingOwnPackage(),
            listedProfiles = profiles.mapTo(mutableSetOf()) { profile -> profile.user }
        )
    }

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
        val canListApps: Boolean = privateSpaceStatus.canListApps(profile = profile)
        val profileApps: List<App> = if (canListApps) {
            profileSource.activities(profile.user).toApps(profile = profile)
        } else {
            emptyList()
        }
        val listedProfiles: Set<UserHandle> = if (canListApps) {
            privateSpaceStatus.listedProfiles.value + user
        } else {
            privateSpaceStatus.listedProfiles.value - user
        }
        publish(
            apps = (_apps.value.orEmpty().filterNot { app -> app.profile == user } + profileApps).excludingOwnPackage(),
            listedProfiles = listedProfiles
        )
    }

    private fun removeProfile(user: UserHandle) {
        _apps.update { current -> current?.filterNot { app -> app.profile == user } }
        privateSpaceStatus.setListedProfiles(profiles = privateSpaceStatus.listedProfiles.value - user)
    }

    // The Apps go out before the listing, so a Space's keys are never released before its Apps list.
    private fun publish(apps: List<App>, listedProfiles: Set<UserHandle>) {
        _apps.value = apps
        privateSpaceStatus.setListedProfiles(profiles = listedProfiles)
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
        val canListApps: Boolean = privateSpaceStatus.canListApps(profile = profile)
        val replacedApps: List<App> = if (!canListApps || packageName == ownPackageName) {
            emptyList()
        } else {
            profileSource.activities(packageName, user).toApps(profile = profile)
        }
        _apps.update { current ->
            current.orEmpty().filterNot { app -> app.profile == user && app.packageName == packageName } + replacedApps
        }
        // Only a full Profile load lists it; a package event may at most revoke that.
        if (!canListApps) {
            privateSpaceStatus.setListedProfiles(profiles = privateSpaceStatus.listedProfiles.value - user)
        }
    }

    private fun removePackage(user: UserHandle, packageName: String) {
        _apps.update { current ->
            current?.filterNot { app -> app.profile == user && app.packageName == packageName }
        }
    }

    private fun List<App>.excludingOwnPackage(): List<App> = filterNot { app -> app.packageName == ownPackageName }

    private fun List<ProfileActivity>.toApps(profile: ProfileInfo): List<App> {
        val representatives: Set<ComponentName> = groupBy { activity -> activity.componentName.packageName }
            .values
            .mapTo(mutableSetOf()) { activities -> activities.minBy { activity -> activity.componentName.className }.componentName }
        return map { activity ->
            activity.toApp(profile = profile, isPackageRepresentative = activity.componentName in representatives)
        }
    }

    private fun ProfileActivity.toApp(profile: ProfileInfo, isPackageRepresentative: Boolean): App = App(
        defaultName = label,
        profileInfo = profile,
        componentName = componentName,
        badgedLabel = badgedLabel,
        isPackageRepresentative = isPackageRepresentative
    )
}
