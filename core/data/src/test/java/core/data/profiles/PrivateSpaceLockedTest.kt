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
class PrivateSpaceLockedTest {
    private val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)

    @Test
    fun `is Locked while quiet`() {
        val actualIsLocked: Boolean = inputPrivateProfile.copy(isQuiet = true).isLocked(isLockRequested = false)

        assertTrue(actualIsLocked)
    }

    @Test
    fun `is Locked while a Lock is requested`() {
        val actualIsLocked: Boolean = inputPrivateProfile.isLocked(isLockRequested = true)

        assertTrue(actualIsLocked)
    }

    @Test
    fun `is not Locked while Unlocked`() {
        val actualIsLocked: Boolean = inputPrivateProfile.isLocked(isLockRequested = false)

        assertFalse(actualIsLocked)
    }
}
