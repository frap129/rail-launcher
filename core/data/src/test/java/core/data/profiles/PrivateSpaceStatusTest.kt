package core.data.profiles

import android.os.UserHandle
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.createProfileInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PrivateSpaceStatusTest {
    private val inputPersonalProfile: ProfileInfo = createProfileInfo(userId = PERSONAL_USER_ID)
    private val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
    private val inputPrivateUser: UserHandle = inputPrivateProfile.user

    @Test
    fun `derives the Private Space answers from its inputs`() {
        val inputCases: List<Case> = listOf(
            Case(
                name = "no space",
                inputs = PrivateSpaceInputs(profiles = listOf(inputPersonalProfile)),
                expected = Expected(
                    isContainerListed = false,
                    isHidden = false,
                    isLocked = false,
                    areKeysKept = false,
                    hasListedApps = false,
                    hasProfile = false
                )
            ),
            Case(
                name = "Unlocked and not listed yet",
                inputs = PrivateSpaceInputs(profiles = listOf(inputPrivateProfile)),
                expected = Expected(isContainerListed = true, isHidden = false, isLocked = false, areKeysKept = true, hasListedApps = false)
            ),
            Case(
                name = "Unlocked and listed",
                inputs = PrivateSpaceInputs(profiles = listOf(inputPrivateProfile), listedProfiles = setOf(inputPrivateUser)),
                expected = Expected(isContainerListed = true, isHidden = false, isLocked = false, areKeysKept = false, hasListedApps = true)
            ),
            Case(
                name = "Locked and not listed",
                inputs = PrivateSpaceInputs(
                    profiles = listOf(inputPrivateProfile.copy(isQuiet = true)),
                    listedProfiles = setOf(inputPrivateUser)
                ),
                expected = Expected(isContainerListed = true, isHidden = false, isLocked = true, areKeysKept = true, hasListedApps = false)
            ),
            Case(
                name = "Lock requested and not listed",
                inputs = PrivateSpaceInputs(
                    profiles = listOf(inputPrivateProfile),
                    isLockRequested = true,
                    listedProfiles = setOf(inputPrivateUser)
                ),
                expected = Expected(isContainerListed = true, isHidden = false, isLocked = true, areKeysKept = true, hasListedApps = false)
            ),
            Case(
                name = "Hidden by Rail while Locked",
                inputs = PrivateSpaceInputs(
                    profiles = listOf(inputPrivateProfile.copy(isQuiet = true)),
                    hideWhenLocked = true
                ),
                expected = Expected(isContainerListed = false, isHidden = true, isLocked = true, areKeysKept = true, hasListedApps = false)
            ),
            Case(
                name = "Hidden by the system while Locked",
                inputs = PrivateSpaceInputs(profiles = listOf(inputPrivateProfile.copy(isQuiet = true, isEntrypointHidden = true))),
                expected = Expected(isContainerListed = false, isHidden = true, isLocked = true, areKeysKept = true, hasListedApps = false)
            ),
            Case(
                name = "Hidden by Rail while a Lock is requested",
                inputs = PrivateSpaceInputs(profiles = listOf(inputPrivateProfile), isLockRequested = true, hideWhenLocked = true),
                expected = Expected(isContainerListed = false, isHidden = true, isLocked = true, areKeysKept = true, hasListedApps = false)
            ),
            Case(
                name = "not Hidden while Unlocked",
                inputs = PrivateSpaceInputs(
                    profiles = listOf(inputPrivateProfile.copy(isEntrypointHidden = true)),
                    hideWhenLocked = true
                ),
                expected = Expected(isContainerListed = true, isHidden = false, isLocked = false, areKeysKept = true, hasListedApps = false)
            ),
            Case(
                name = "no space with a Lock requested",
                inputs = PrivateSpaceInputs(profiles = listOf(inputPersonalProfile), isLockRequested = true),
                expected = Expected(
                    isContainerListed = false,
                    isHidden = false,
                    isLocked = true,
                    areKeysKept = false,
                    hasListedApps = false,
                    hasProfile = false
                )
            ),
            Case(
                name = "space removed after listing",
                inputs = PrivateSpaceInputs(profiles = listOf(inputPersonalProfile), listedProfiles = setOf(inputPrivateUser)),
                expected = Expected(
                    isContainerListed = false,
                    isHidden = false,
                    isLocked = false,
                    areKeysKept = false,
                    hasListedApps = false,
                    hasProfile = false
                )
            )
        )

        inputCases.forEach { case ->
            val actualStatus: PrivateSpaceStatus = derivePrivateSpaceStatus(inputs = case.inputs)

            assertEquals(case.name, case.expected.isContainerListed, actualStatus.isContainerListed)
            assertEquals(case.name, case.expected.isHidden, actualStatus.isHidden)
            assertEquals(case.name, case.expected.isLocked, actualStatus.isLocked)
            assertEquals(case.name, case.expected.areKeysKept, actualStatus.areKeysKept)
            assertEquals(case.name, case.expected.hasListedApps, actualStatus.hasListedApps)
            assertEquals(case.name, case.expected.hasProfile, actualStatus.profile != null)
        }
    }

    private data class Case(val name: String, val inputs: PrivateSpaceInputs, val expected: Expected)

    private data class Expected(
        val isContainerListed: Boolean,
        val isHidden: Boolean,
        val isLocked: Boolean,
        val areKeysKept: Boolean,
        val hasListedApps: Boolean,
        val hasProfile: Boolean = true
    )
}
