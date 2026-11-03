package core.data.profiles

import core.data.prefs.Settings
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.TEST_SDK
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createPrivateSpaceLock
import core.data.testing.createPrivateSpaceStatus
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import core.data.testing.finishPendingWork
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IPrivateSpaceStatusImplTest {
    private val inputLockedPrivateProfile: ProfileInfo = createProfileInfo(
        userId = PRIVATE_USER_ID,
        kind = ProfileKind.Private,
        isQuiet = true
    )
    private val inputUnlockedPrivateProfile: ProfileInfo = inputLockedPrivateProfile.copy(isQuiet = false)

    @Test
    fun `follows the hide setting loaded after startup`() = runTest {
        val inputSettings: MutableSharedFlow<Settings> = MutableSharedFlow(replay = 1)
        val privateSpaceStatus: IPrivateSpaceStatus = createPrivateSpaceStatus(
            profileSource = createProfileSourceMock(profile = inputLockedPrivateProfile),
            preferencesRepository = createPreferencesRepositoryMock(settingsFlow = inputSettings)
        )

        assertEquals(inputLockedPrivateProfile, privateSpaceStatus.status.value.profile)
        assertTrue(privateSpaceStatus.status.value.isHidden)
        assertEquals(setOf(inputLockedPrivateProfile.user.toProfileToken()), privateSpaceStatus.status.value.keptKeys)

        inputSettings.emit(Settings(hidePrivateSpaceWhenLocked = false))
        finishPendingWork()
        assertTrue(privateSpaceStatus.status.value.isContainerListed)

        inputSettings.emit(Settings(hidePrivateSpaceWhenLocked = true))
        finishPendingWork()
        assertFalse(privateSpaceStatus.status.value.isContainerListed)
    }

    @Test
    fun `reports each Private Space input change through currentStatus`() = runTest {
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputUnlockedPrivateProfile))
        val mockProfileSource: IProfileSource = createProfileSourceMock(state = inputProfileState)
        val inputLock: IPrivateSpaceLock = createPrivateSpaceLock(profileSource = mockProfileSource)
        val inputSettings: MutableStateFlow<Settings> = MutableStateFlow(Settings(hidePrivateSpaceWhenLocked = false))
        val privateSpaceStatus: IPrivateSpaceStatus = createPrivateSpaceStatus(
            profileSource = mockProfileSource,
            privateSpaceLock = inputLock,
            preferencesRepository = createPreferencesRepositoryMock(settingsFlow = inputSettings)
        )
        finishPendingWork()
        val actualInitialStatus: PrivateSpaceStatus = privateSpaceStatus.currentStatus()
        assertEquals(inputUnlockedPrivateProfile, actualInitialStatus.profile)
        assertFalse(actualInitialStatus.isLocked)
        assertFalse(actualInitialStatus.isHidden)
        assertFalse(actualInitialStatus.hasListedApps)

        inputProfileState.profiles.value = listOf(inputLockedPrivateProfile)
        finishPendingWork()
        val actualLockedStatus: PrivateSpaceStatus = privateSpaceStatus.currentStatus()
        assertEquals(inputLockedPrivateProfile, actualLockedStatus.profile)
        assertTrue(actualLockedStatus.isLocked)
        assertFalse(actualLockedStatus.isHidden)
        assertFalse(actualLockedStatus.hasListedApps)

        inputProfileState.profiles.value = listOf(inputUnlockedPrivateProfile)
        finishPendingWork()
        val actualUnlockedStatus: PrivateSpaceStatus = privateSpaceStatus.currentStatus()
        assertEquals(inputUnlockedPrivateProfile, actualUnlockedStatus.profile)
        assertFalse(actualUnlockedStatus.isLocked)
        assertFalse(actualUnlockedStatus.hasListedApps)

        inputLock.requestLock()
        finishPendingWork()
        val actualLockRequestedStatus: PrivateSpaceStatus = privateSpaceStatus.currentStatus()
        assertTrue(actualLockRequestedStatus.isLocked)
        assertFalse(actualLockRequestedStatus.isHidden)

        inputSettings.value = Settings(hidePrivateSpaceWhenLocked = true)
        finishPendingWork()
        val actualHiddenStatus: PrivateSpaceStatus = privateSpaceStatus.currentStatus()
        assertTrue(actualHiddenStatus.isLocked)
        assertTrue(actualHiddenStatus.isHidden)
        assertFalse(actualHiddenStatus.isContainerListed)

        inputLock.clearLockRequest()
        privateSpaceStatus.setListedProfiles(setOf(inputUnlockedPrivateProfile.user))
        finishPendingWork()
        val actualListedStatus: PrivateSpaceStatus = privateSpaceStatus.currentStatus()
        assertEquals(inputUnlockedPrivateProfile, actualListedStatus.profile)
        assertFalse(actualListedStatus.isLocked)
        assertFalse(actualListedStatus.isHidden)
        assertTrue(actualListedStatus.hasListedApps)
    }

    @Test
    fun `lists a Private Space's Apps while a Lock is requested`() = runTest {
        val mockProfileSource: IProfileSource = createProfileSourceMock(profile = inputUnlockedPrivateProfile)
        val inputLock: IPrivateSpaceLock = createPrivateSpaceLock(profileSource = mockProfileSource)
        inputLock.requestLock()
        val privateSpaceStatus: IPrivateSpaceStatus = createPrivateSpaceStatus(
            profileSource = mockProfileSource,
            privateSpaceLock = inputLock
        )

        assertTrue(inputLock.isLockRequested.value)
        assertTrue(privateSpaceStatus.currentStatus().isLocked)
        assertTrue(privateSpaceStatus.canListApps(profile = inputUnlockedPrivateProfile))
    }

    @Test
    fun `does not list the Apps of a quiet Private Space`() = runTest {
        val privateSpaceStatus: IPrivateSpaceStatus = createPrivateSpaceStatus(
            profileSource = createProfileSourceMock(profile = inputLockedPrivateProfile)
        )

        assertFalse(privateSpaceStatus.canListApps(profile = inputLockedPrivateProfile))
    }
}
