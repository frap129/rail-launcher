package core.data.profiles

import android.content.ComponentName
import android.content.IntentSender
import android.graphics.drawable.Drawable
import android.os.UserHandle
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** The kind of Profile an App belongs to. */
enum class ProfileKind {
    Personal,
    Work,
    Private,
    Other;

    /** Whether this is the personal Profile, which takes no Profile badge. */
    val isPersonal: Boolean get() = this == Personal
}

/** A Profile Rail can load Apps from. */
data class ProfileInfo(
    val user: UserHandle,
    val serial: Long,
    val kind: ProfileKind,
    val isQuiet: Boolean,
    /** Whether the system's "Hide private space" setting hides this Profile's entrypoint (API 36+). */
    val isEntrypointHidden: Boolean = false
)

/** Whether this Profile is the Private Space. */
val ProfileInfo.isPrivate: Boolean get() = kind == ProfileKind.Private

/** Whether this Private Space is Locked, counting a Lock requested before the system confirms. */
fun ProfileInfo.isLocked(isLockRequested: Boolean): Boolean = isQuiet || isLockRequested

/**
 * Whether this Private Space is Hidden: Locked, or with a Lock requested, and Rail's switch or
 * the system's "Hide private space" setting hides its entrypoint.
 */
fun ProfileInfo.isHiddenPrivateSpace(hideWhenLocked: Boolean, isLockRequested: Boolean): Boolean =
    isLocked(isLockRequested = isLockRequested) && (hideWhenLocked || isEntrypointHidden)

/** The listed Private Space Profile, or null when the device has none or Rail cannot see it. */
val List<ProfileInfo>.privateSpace: ProfileInfo? get() = firstOrNull { profile -> profile.isPrivate }

/** A launchable activity resolved for one Profile. */
data class ProfileActivity(val componentName: ComponentName, val user: UserHandle, val label: String, val badgedLabel: String)

/** A package or Profile change Rail must react to. */
sealed interface ProfileChange {
    val user: UserHandle

    data class PackageAdded(override val user: UserHandle, val packageName: String) : ProfileChange

    data class PackageRemoved(override val user: UserHandle, val packageName: String) : ProfileChange

    data class PackageChanged(override val user: UserHandle, val packageName: String) : ProfileChange

    data class PackagesUnavailable(override val user: UserHandle, val packageNames: List<String>, val replacing: Boolean) : ProfileChange

    data class ProfileAdded(override val user: UserHandle) : ProfileChange

    data class ProfileRemoved(override val user: UserHandle) : ProfileChange

    data class ProfileAvailable(override val user: UserHandle) : ProfileChange

    data class ProfileUnavailable(override val user: UserHandle) : ProfileChange
}

/** The outcome of asking a Profile to change quiet mode. */
sealed interface QuietRequestResult {
    /** Quiet mode changed. */
    data object Changed : QuietRequestResult

    /** The system shows its credential screen before it changes quiet mode. */
    data object AwaitingCredential : QuietRequestResult

    /** The system refused the change. */
    data object Failed : QuietRequestResult
}

/** The only source that enumerates Profiles and reads their launchable activities. */
interface IProfileSource {
    val profiles: StateFlow<List<ProfileInfo>>

    /** Package and Profile changes, emitted for the lifetime of the process. */
    val changes: SharedFlow<ProfileChange>

    /**
     * Re-reads the system Profiles, so a Profile the platform started reporting is listed without a restart.
     * Availability changes are published through [changes].
     */
    suspend fun refreshProfiles()

    /** The Profile for [user], or null when it is no longer listed. */
    fun profileFor(user: UserHandle): ProfileInfo?

    /** The kind of [user]'s Profile, or [ProfileKind.Other] when it is no longer listed. */
    fun kindFor(user: UserHandle): ProfileKind

    /** The system-badged form of [label] for [user]. */
    fun badgedLabel(label: String, user: UserHandle): String

    /** Every launchable activity in [user]. */
    fun activities(user: UserHandle): List<ProfileActivity>

    /** The launchable activities of [packageName] in [user]. */
    fun activities(packageName: String, user: UserHandle): List<ProfileActivity>

    /** The unbadged launcher activity icon of [componentName] in [user]'s Profile, or null when it cannot be loaded. */
    fun activityIcon(componentName: ComponentName, user: UserHandle): Drawable?

    /** The unbadged application icon of [packageName] in [user]'s Profile, or null when it cannot be loaded. */
    fun applicationIcon(packageName: String, user: UserHandle): Drawable?

    /** Asks the system to change [user]'s quiet mode. */
    suspend fun requestQuiet(user: UserHandle, quiet: Boolean): QuietRequestResult

    /** The system's Private Space settings screen, or null when the system does not offer one (API 36+). */
    suspend fun privateSpaceSettingsIntent(): IntentSender?

    /** The Profile whose serial number is [serial], or null when no Profile matches. */
    fun userForSerial(serial: Long): UserHandle?

    /** The serial number of [user], or null when the Profile no longer exists. */
    fun serialForUser(user: UserHandle): Long?
}
