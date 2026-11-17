package core.baselineprofile

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import core.ui.HOME_FULLY_DRAWN_TEST_TAG

private const val FULLY_DRAWN_TIMEOUT_MS = 10_000L

// Compose test tags keep the package out of the resource id, so the node's package pins it to Rail.
private val HOME_FULLY_DRAWN_SELECTOR: BySelector = By.res(HOME_FULLY_DRAWN_TEST_TAG).pkg(APP_PACKAGE)

/** Waits until Rail reports Home fully drawn, after its Header, Widget Area and Favorites are shown. */
internal fun waitForHomeFullyDrawn(device: UiDevice) {
    check(device.wait(Until.hasObject(HOME_FULLY_DRAWN_SELECTOR), FULLY_DRAWN_TIMEOUT_MS)) {
        "Rail did not report Home fully drawn"
    }
}
