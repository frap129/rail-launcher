package core.data.apps.model

import android.os.UserHandle

/** The package and user profile that identify an App. */
data class AppProfile(val packageName: String, val profile: UserHandle)
