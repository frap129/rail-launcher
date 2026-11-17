package core.data.profiles

import android.os.UserHandle
import core.data.prefs.IPreferencesRepository
import core.data.prefs.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Derives the Private Space status from Profiles, Lock requests, the hide setting and listing. */
class IPrivateSpaceStatusImpl(
    private val profileSource: IProfileSource,
    private val privateSpaceLock: IPrivateSpaceLock,
    private val preferencesRepository: IPreferencesRepository,
    scope: CoroutineScope
) : IPrivateSpaceStatus {
    private val listedProfilesState: MutableStateFlow<Set<UserHandle>> = MutableStateFlow(emptySet())
    override val listedProfiles: StateFlow<Set<UserHandle>> = listedProfilesState.asStateFlow()

    // A Private Space is unknown until its Profile loads, and stays hidden until the hide setting loads.
    private val loadedProfiles: Flow<List<ProfileInfo>?> = combine(
        profileSource.isLoaded,
        profileSource.profiles
    ) { isLoaded, profiles ->
        profiles.takeIf { isLoaded }
    }
    private val settingsBeforeLoad: Flow<Settings?> = preferencesRepository.settings
        .map<Settings, Settings?> { settings -> settings }
        .onStart { emit(null) }

    override val status: StateFlow<PrivateSpaceStatus> = combine(
        loadedProfiles,
        privateSpaceLock.isLockRequested,
        settingsBeforeLoad,
        listedProfiles
    ) { profiles, isLockRequested, settings, listed ->
        if (profiles == null) {
            PrivateSpaceStatus()
        } else {
            statusFor(
                profiles = profiles,
                isLockRequested = isLockRequested,
                hideWhenLocked = settings?.hidePrivateSpaceWhenLocked ?: true,
                listedProfiles = listed
            )
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = statusBeforeSettingsLoad()
    )

    override fun currentStatus(): PrivateSpaceStatus = status.value

    override fun canListApps(profile: ProfileInfo): Boolean = profile.isListable()

    override fun setListedProfiles(profiles: Set<UserHandle>) {
        listedProfilesState.value = profiles
    }

    /** Hides a Locked Private Space once its Profile loads, until settings report the user's choice. */
    private fun statusBeforeSettingsLoad(): PrivateSpaceStatus {
        if (!profileSource.isLoaded.value) {
            return PrivateSpaceStatus()
        }
        return statusFor(
            profiles = profileSource.profiles.value,
            isLockRequested = privateSpaceLock.isLockRequested.value,
            hideWhenLocked = true,
            listedProfiles = listedProfilesState.value
        )
    }

    private fun statusFor(
        profiles: List<ProfileInfo>,
        isLockRequested: Boolean,
        hideWhenLocked: Boolean,
        listedProfiles: Set<UserHandle>
    ): PrivateSpaceStatus = derivePrivateSpaceStatus(
        PrivateSpaceInputs(
            profiles = profiles,
            isLockRequested = isLockRequested,
            hideWhenLocked = hideWhenLocked,
            listedProfiles = listedProfiles
        )
    )
}
