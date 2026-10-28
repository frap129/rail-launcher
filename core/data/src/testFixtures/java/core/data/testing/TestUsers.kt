package core.data.testing

import android.os.UserHandle
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind

const val PERSONAL_USER_ID: Int = 0
const val WORK_USER_ID: Int = 10
const val PRIVATE_USER_ID: Int = 11
private const val USER_ID_OFFSET: Int = 100_000

/** The [UserHandle] of [userId], where user 0 is the personal Profile. */
fun createUserHandle(userId: Int): UserHandle = UserHandle.getUserHandleForUid(userId * USER_ID_OFFSET)

/** A [ProfileInfo] for [userId], using the user id as its serial number. */
fun createProfileInfo(userId: Int, kind: ProfileKind = ProfileKind.Personal, isQuiet: Boolean = false): ProfileInfo = ProfileInfo(
    user = createUserHandle(userId = userId),
    serial = userId.toLong(),
    kind = kind,
    isQuiet = isQuiet
)
