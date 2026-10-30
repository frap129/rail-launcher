package core.data.testing

import core.data.profiles.IPrivateSpaceLock
import core.data.profiles.IPrivateSpaceLockImpl
import core.data.profiles.IProfileSource
import kotlinx.coroutines.test.TestScope

/** The production Private Space Lock over [profileSource], collecting on the test's background scope. */
fun TestScope.createPrivateSpaceLock(profileSource: IProfileSource = createProfileSourceMock()): IPrivateSpaceLock =
    IPrivateSpaceLockImpl(profileSource = profileSource, scope = backgroundScope)
