package core.data.testing

import android.os.UserHandle

const val PERSONAL_USER_ID: Int = 0
const val WORK_USER_ID: Int = 10
const val PRIVATE_USER_ID: Int = 11
private const val USER_ID_OFFSET: Int = 100_000

/** The [UserHandle] of [userId], where user 0 is the personal Profile. */
fun createUserHandle(userId: Int): UserHandle = UserHandle.getUserHandleForUid(userId * USER_ID_OFFSET)
