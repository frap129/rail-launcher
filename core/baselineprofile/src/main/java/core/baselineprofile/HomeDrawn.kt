package core.baselineprofile

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import core.ui.HOME_FULLY_DRAWN_TEST_TAG
import core.ui.HOME_ICONS_DRAWN_TEST_TAG

private const val HOME_DRAWN_TIMEOUT_MS = 10_000L

/** Waits until Rail reports Home fully drawn, after its Header, Widget Area and Favorites are shown. */
internal fun waitForHomeFullyDrawn(device: UiDevice) {
    check(waitForHomeTag(device = device, tag = HOME_FULLY_DRAWN_TEST_TAG)) {
        "Rail did not report Home fully drawn"
    }
}

/** Waits until Home has drawn the Favorite icons it shows, or failed to. */
internal fun waitForHomeIconsDrawn(device: UiDevice) {
    check(waitForHomeTag(device = device, tag = HOME_ICONS_DRAWN_TEST_TAG)) {
        "Rail did not report Home icons drawn"
    }
}

private fun waitForHomeTag(device: UiDevice, tag: String): Boolean =
    device.wait(Until.hasObject(homeTagSelector(tag = tag)), HOME_DRAWN_TIMEOUT_MS)

// Compose test tags keep the package out of the resource id, so the node's package pins it to Rail.
private fun homeTagSelector(tag: String): BySelector = By.res(tag).pkg(APP_PACKAGE)
