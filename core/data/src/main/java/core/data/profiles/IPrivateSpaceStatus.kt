package core.data.profiles

import android.os.UserHandle
import kotlinx.coroutines.flow.StateFlow

/**
 * The answers Rail derives about the Private Space from its Profile, Lock requests, the hide
 * settings and the Apps Rail has listed. Consumers read these answers instead of combining the
 * raw Profile inputs themselves.
 */
data class PrivateSpaceStatus(
    /** The listed Private Space Profile, or null when the device has none. */
    val profile: ProfileInfo? = null,
    /** Whether the container is Hidden while Locked by Rail's switch or the system setting. */
    val isHidden: Boolean = false,
    /** Whether the Private Space is Locked, counting a Lock requested before the system confirms. */
    val isLocked: Boolean = false,
    /** Whether the Private Space has listed its Apps while Unlocked. */
    val hasListedApps: Boolean = false
) {
    /** Whether Rail shows the Private Space container. */
    val isContainerListed: Boolean get() = profile != null && !isHidden

    /** Whether the Private Space exists and is not Locked. */
    val isUnlocked: Boolean get() = profile != null && !isLocked

    /** Whether Rail keeps the Private Space's Launchable and Shortcut keys. */
    val areKeysKept: Boolean get() = profile != null && !hasListedApps

    /** The Profile tokens of the Private Space's keys Rail keeps. */
    val keptKeys: Set<ProfileToken> get() = if (areKeysKept) profileTokens() else emptySet()

    /** The Profile tokens of the Private Space whose Apps Rail has listed. */
    val enumeratedKeys: Set<ProfileToken> get() = if (hasListedApps) profileTokens() else emptySet()

    private fun profileTokens(): Set<ProfileToken> = profile?.let { current -> setOf(current.user.toProfileToken()) }.orEmpty()
}

/** The inputs the Private Space status is computed from. */
data class PrivateSpaceInputs(
    val profiles: List<ProfileInfo> = emptyList(),
    val isLockRequested: Boolean = false,
    val hideWhenLocked: Boolean = false,
    val listedProfiles: Set<UserHandle> = emptySet()
)

/** Derives the Private Space status from [inputs]. */
fun derivePrivateSpaceStatus(inputs: PrivateSpaceInputs): PrivateSpaceStatus {
    val profile: ProfileInfo? = inputs.profiles.privateSpace
    val isLocked: Boolean = inputs.isLockRequested || profile?.isQuiet == true
    val isHidden: Boolean = profile != null && isLocked && (inputs.hideWhenLocked || profile.isEntrypointHidden)
    val hasListedApps: Boolean = profile != null && !isLocked && profile.user in inputs.listedProfiles
    return PrivateSpaceStatus(
        profile = profile,
        isHidden = isHidden,
        isLocked = isLocked,
        hasListedApps = hasListedApps
    )
}

/** The shared Private Space status and the Apps Rail has listed for its Profile. */
interface IPrivateSpaceStatus {
    /** The current Private Space status; a Locked Private Space starts Hidden until settings load. */
    val status: StateFlow<PrivateSpaceStatus>

    /** The latest [status] value, for callers that cannot suspend. */
    fun currentStatus(): PrivateSpaceStatus

    /** The Profiles whose Apps Rail has listed, including those that listed none. */
    val listedProfiles: StateFlow<Set<UserHandle>>

    /** Whether Rail may read [profile]'s Apps; every Profile except a Private Space in quiet mode. */
    fun canListApps(profile: ProfileInfo): Boolean

    /** Records the Profiles whose Apps Rail has listed, replacing the previous set. */
    fun setListedProfiles(profiles: Set<UserHandle>)
}
