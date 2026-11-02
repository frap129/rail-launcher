package core.data.testing

import core.data.prefs.IPreferencesRepository
import core.data.profiles.IPrivateSpaceLock
import core.data.profiles.IPrivateSpaceStatus
import core.data.profiles.IPrivateSpaceStatusImpl
import core.data.profiles.IProfileSource
import core.data.profiles.PrivateSpaceStatus
import core.data.profiles.ProfileInfo
import core.data.profiles.isPrivate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope

/** The production Private Space status over [profileSource], collecting on the test's background scope. */
fun TestScope.createPrivateSpaceStatus(
    profileSource: IProfileSource = createProfileSourceMock(),
    privateSpaceLock: IPrivateSpaceLock = createPrivateSpaceLock(profileSource = profileSource),
    preferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
): IPrivateSpaceStatus = IPrivateSpaceStatusImpl(
    profileSource = profileSource,
    privateSpaceLock = privateSpaceLock,
    preferencesRepository = preferencesRepository,
    scope = backgroundScope
)

/** A fixed Private Space status for consumer tests. */
fun createFixedPrivateSpaceStatus(
    profile: ProfileInfo? = null,
    isHidden: Boolean = false,
    isLocked: Boolean = false,
    hasListedApps: Boolean = false
): PrivateSpaceStatus = PrivateSpaceStatus(
    profile = profile,
    isHidden = isHidden,
    isLocked = isLocked,
    hasListedApps = hasListedApps
)

/** A MockK [IPrivateSpaceStatus] reading [status], which tests can update. */
fun createPrivateSpaceStatusMock(status: StateFlow<PrivateSpaceStatus> = MutableStateFlow(PrivateSpaceStatus())): IPrivateSpaceStatus {
    val mockPrivateSpaceStatus: IPrivateSpaceStatus = mockk(relaxed = true)
    every { mockPrivateSpaceStatus.status } returns status
    every { mockPrivateSpaceStatus.currentStatus() } answers { status.value }
    every { mockPrivateSpaceStatus.listedProfiles } returns MutableStateFlow(emptySet())
    every { mockPrivateSpaceStatus.canListApps(any()) } answers {
        val profile: ProfileInfo = firstArg()
        !profile.isPrivate || (status.value.profile?.user == profile.user && !status.value.isLocked)
    }
    return mockPrivateSpaceStatus
}
