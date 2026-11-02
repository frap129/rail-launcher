package core.data.profiles

import android.os.UserHandle
import core.data.prefs.IPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Derives the Private Space status from Profiles, Lock requests, the hide setting and listing. */
class IPrivateSpaceStatusImpl(
    private val profileSource: IProfileSource,
    private val privateSpaceLock: IPrivateSpaceLock,
    private val preferencesRepository: IPreferencesRepository,
    scope: CoroutineScope
) : IPrivateSpaceStatus {
    private val listedProfilesState: MutableStateFlow<Set<UserHandle>> = MutableStateFlow(emptySet())
    private val hideWhenLocked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val listedProfiles: StateFlow<Set<UserHandle>> = listedProfilesState.asStateFlow()

    override val status: StateFlow<PrivateSpaceStatus> = combine(
        profileSource.profiles,
        privateSpaceLock.isLockRequested,
        hideWhenLocked,
        listedProfiles
    ) { profiles, isLockRequested, hide, listed ->
        derivePrivateSpaceStatus(
            PrivateSpaceInputs(
                profiles = profiles,
                isLockRequested = isLockRequested,
                hideWhenLocked = hide,
                listedProfiles = listed
            )
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = currentStatus()
    )

    init {
        scope.launch {
            preferencesRepository.settings
                .map { settings -> settings.hidePrivateSpaceWhenLocked }
                .distinctUntilChanged()
                .collect { hide -> hideWhenLocked.value = hide }
        }
    }

    override fun currentStatus(): PrivateSpaceStatus = derivePrivateSpaceStatus(
        PrivateSpaceInputs(
            profiles = profileSource.profiles.value,
            isLockRequested = privateSpaceLock.isLockRequested.value,
            hideWhenLocked = hideWhenLocked.value,
            listedProfiles = listedProfilesState.value
        )
    )

    override fun canListApps(profile: ProfileInfo): Boolean {
        if (!profile.isPrivate) {
            return true
        }
        val status: PrivateSpaceStatus = currentStatus()
        return status.profile?.user == profile.user && !status.isLocked
    }

    override fun setListedProfiles(profiles: Set<UserHandle>) {
        listedProfilesState.value = profiles
    }
}
