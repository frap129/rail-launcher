package core.data.profiles

import android.os.UserHandle

/** The stored identity token of a Profile: the text form of its [UserHandle]. */
@JvmInline
value class ProfileToken(val value: String) {
    override fun toString(): String = value
}

/** The token [this] Profile is stored under in Launchable and Shortcut keys. */
fun UserHandle.toProfileToken(): ProfileToken = ProfileToken(value = toString())
