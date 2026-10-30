package core.data.profiles

import core.data.testing.PRIVATE_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.TEST_SDK
import core.data.testing.createPrivateSpaceLock
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PrivateSpaceLockTest {
    private val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)

    @Test
    fun `requests the lock until the Private Space reports itself quiet`() = runTest {
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile))
        val lock: IPrivateSpaceLock = createLock(profileState = inputProfileState)

        lock.requestLock()
        assertTrue(lock.isLockRequested.value)

        inputProfileState.profiles.value = listOf(inputPrivateProfile.copy(isQuiet = true))
        advanceUntilIdle()
        runCurrent()

        assertFalse(lock.isLockRequested.value)
    }

    @Test
    fun `clears the lock request when it settles`() = runTest {
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile))
        val lock: IPrivateSpaceLock = createLock(profileState = inputProfileState)

        lock.requestLock()
        lock.clearLockRequest()

        assertFalse(lock.isLockRequested.value)
    }

    @Test
    fun `clears the lock request when the Private Space is removed`() = runTest {
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile))
        val lock: IPrivateSpaceLock = createLock(profileState = inputProfileState)

        lock.requestLock()
        inputProfileState.profiles.value = emptyList()
        advanceUntilIdle()
        runCurrent()

        assertFalse(lock.isLockRequested.value)
    }

    private fun TestScope.createLock(profileState: ProfileSourceState): IPrivateSpaceLock =
        createPrivateSpaceLock(profileSource = createProfileSourceMock(state = profileState))
}
