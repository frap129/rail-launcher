package core.data.profiles

import android.content.ComponentName
import android.os.UserHandle
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** The kind of Profile an App belongs to. */
enum class ProfileKind {
    Personal,
    Work,
    Private,
    Other
}

/** A Profile Rail can load Apps from. */
data class ProfileInfo(val user: UserHandle, val serial: Long, val kind: ProfileKind, val isQuiet: Boolean)

/** A launchable activity resolved for one Profile. */
data class ProfileActivity(val componentName: ComponentName, val user: UserHandle, val label: String)

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

/** The only source that enumerates Profiles and reads their launchable activities. */
interface IProfileSource {
    val profiles: StateFlow<List<ProfileInfo>>

    /** Package and Profile changes, emitted for the lifetime of the process. */
    val changes: SharedFlow<ProfileChange>

    /** The Profile for [user], or null when it is no longer listed. */
    fun profileFor(user: UserHandle): ProfileInfo?

    /** Every launchable activity in [user]. */
    fun activities(user: UserHandle): List<ProfileActivity>

    /** The launchable activities of [packageName] in [user]. */
    fun activities(packageName: String, user: UserHandle): List<ProfileActivity>

    /** Asks the system to change [user]'s quiet mode. */
    suspend fun requestQuiet(user: UserHandle, quiet: Boolean)

    /** The Profile whose serial number is [serial], or null when no Profile matches. */
    fun userForSerial(serial: Long): UserHandle?

    /** The serial number of [user], or null when the Profile no longer exists. */
    fun serialForUser(user: UserHandle): Long?
}
