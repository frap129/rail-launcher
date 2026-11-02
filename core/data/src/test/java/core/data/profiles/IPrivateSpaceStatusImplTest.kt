package core.data.profiles

import core.data.prefs.Settings
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createPrivateSpaceLock
import core.data.testing.createPrivateSpaceStatus
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
        advanceUntilIdle()
        runCurrent()
        assertTrue(privateSpaceStatus.status.value.isContainerListed)

        inputSettings.emit(Settings(hidePrivateSpaceWhenLocked = true))
        advanceUntilIdle()
        runCurrent()
        assertFalse(privateSpaceStatus.status.value.isContainerListed)
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
