package core.data.profiles

import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.createProfileInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PrivateSpaceHiddenTest {
    private val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)

    @Test
    fun `is hidden while Locked when Rail hides it`() {
        val actualIsHidden: Boolean = inputPrivateProfile.copy(isQuiet = true)
            .isHiddenPrivateSpace(hideWhenLocked = true, isLockRequested = false)

        assertTrue(actualIsHidden)
    }

    @Test
    fun `is hidden while Locked when the system hides it`() {
        val actualIsHidden: Boolean = inputPrivateProfile.copy(isQuiet = true, isEntrypointHidden = true)
            .isHiddenPrivateSpace(hideWhenLocked = false, isLockRequested = false)

        assertTrue(actualIsHidden)
    }

    @Test
    fun `is not hidden while Locked when nothing hides it`() {
        val actualIsHidden: Boolean = inputPrivateProfile.copy(isQuiet = true)
            .isHiddenPrivateSpace(hideWhenLocked = false, isLockRequested = false)

        assertFalse(actualIsHidden)
    }

    @Test
    fun `is not hidden while Unlocked when Rail and the system hide it`() {
        val actualIsHidden: Boolean = inputPrivateProfile.copy(isEntrypointHidden = true)
            .isHiddenPrivateSpace(hideWhenLocked = true, isLockRequested = false)

        assertFalse(actualIsHidden)
    }

    @Test
    fun `is hidden while a Lock is requested when Rail hides it`() {
        val actualIsHidden: Boolean = inputPrivateProfile.isHiddenPrivateSpace(hideWhenLocked = true, isLockRequested = true)

        assertTrue(actualIsHidden)
    }

    @Test
    fun `is not hidden while a Lock is requested when nothing hides it`() {
        val actualIsHidden: Boolean = inputPrivateProfile.isHiddenPrivateSpace(hideWhenLocked = false, isLockRequested = true)

        assertFalse(actualIsHidden)
    }
}
