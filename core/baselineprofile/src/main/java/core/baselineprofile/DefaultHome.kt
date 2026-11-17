package core.baselineprofile

import androidx.test.uiautomator.UiDevice

internal const val APP_PACKAGE: String = "dev.maples.rail"
internal const val APP_HOME_ACTIVITY: String = "$APP_PACKAGE/.MainActivity"

private const val FALLBACK_HOME_ACTIVITY: String = "com.android.settings/.FallbackHome"
private const val RESOLVE_HOME_COMMAND =
    "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
private const val QUERY_HOMES_COMMAND =
    "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"

/** The activity the device uses as its default Home, or blank when the device reports none. */
internal fun resolveDefaultHome(device: UiDevice): String = device.executeShellCommand(RESOLVE_HOME_COMMAND)
    .lineSequence()
    .map(String::trim)
    .lastOrNull(::isComponentLine)
    .orEmpty()

/**
 * A Home other than Rail's to keep the default while a benchmark kills Rail, or null when the
 * device installs none. [previousHome] serves when it is one; otherwise the first installed Home
 * is chosen, skipping the fallback Home that only runs before the user unlocks.
 */
internal fun resolveHomeToHold(device: UiDevice, previousHome: String): String? {
    if (isOtherHome(homeActivity = previousHome)) {
        return previousHome
    }
    return device.executeShellCommand(QUERY_HOMES_COMMAND)
        .lineSequence()
        .map(String::trim)
        .firstOrNull { homeActivity -> isComponentLine(line = homeActivity) && isOtherHome(homeActivity = homeActivity) }
}

/** Whether [homeActivity] is a usable default Home other than Rail's. */
internal fun isOtherHome(homeActivity: String): Boolean =
    homeActivity.isNotBlank() && homeActivity != FALLBACK_HOME_ACTIVITY && !homeActivity.contains(APP_PACKAGE)

/** Makes [homeActivity] the device's default Home. */
internal fun setDefaultHome(device: UiDevice, homeActivity: String) {
    device.executeShellCommand("cmd package set-home-activity $homeActivity")
}

/** Runs [block] with the device's current default Home, restoring it afterwards. */
internal fun <T> withPreviousHomeRestored(device: UiDevice, block: (previousHome: String) -> T): T {
    val previousHome: String = resolveDefaultHome(device = device)
    return try {
        block(previousHome)
    } finally {
        if (previousHome.isNotBlank()) {
            setDefaultHome(device = device, homeActivity = previousHome)
        }
    }
}

// PackageManager prints each component on its own line after the count and Activity headers.
private fun isComponentLine(line: String): Boolean = line.count { character -> character == '/' } == 1 && !line.startsWith("/")
