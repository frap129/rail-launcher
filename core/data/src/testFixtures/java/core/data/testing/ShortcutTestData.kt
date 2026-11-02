package core.data.testing

import android.os.UserHandle
import core.data.profiles.ProfileInfo
import core.data.shortcuts.ShortcutLaunchable

const val FIRST_SHORTCUT_ID: String = "first"
const val SECOND_SHORTCUT_ID: String = "second"

/** A Shortcut with test labels and [profile]. */
fun createShortcut(
    packageName: String,
    profile: UserHandle = createUserHandle(userId = PERSONAL_USER_ID),
    shortcutId: String = FIRST_SHORTCUT_ID,
    shortLabel: String = "Shortcut",
    longLabel: String = shortLabel,
    isEnabled: Boolean = true,
    profileInfo: ProfileInfo? = null
): ShortcutLaunchable = ShortcutLaunchable(
    packageName = packageName,
    shortcutId = shortcutId,
    profile = profile,
    shortLabel = shortLabel,
    longLabel = longLabel,
    isEnabled = isEnabled,
    profileInfo = profileInfo
)
