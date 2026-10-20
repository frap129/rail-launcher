package core.baselineprofile

import android.graphics.Point
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = "dev.maples.rail",
            includeInStartupProfile = true
        ) {
            // Start default activity
            pressHome()
            startActivityAndWait()

            // Open the app list from Home using the ScrollRail.
            device.wait(Until.hasObject(By.res("launcherHome")), 5_000)
            val scrollRail = checkNotNull(device.wait(Until.findObject(By.res("scrollRail")), 5_000)) {
                "ScrollRail not found"
            }
            scrollRail.click()

            // Scroll launcher
            val launcherList = checkNotNull(device.wait(Until.findObject(By.res("launcherList")), 5_000)) {
                "Launcher list not found after opening the ScrollRail"
            }
            launcherList.fling(Direction.DOWN, 10000)
            launcherList.fling(Direction.UP, 10000)

            // Interact with the ScrollRail
            val scrollRailForDrag = checkNotNull(device.wait(Until.findObject(By.res("scrollRail")), 5_000)) {
                "ScrollRail not found after opening the app list"
            }
            val point = Point(device.displayWidth, device.displayHeight)
            scrollRailForDrag.longClick()
            scrollRailForDrag.drag(point, 1000)

            // TODO Write more interactions to optimize advanced journeys of your app.
        }
    }
}
