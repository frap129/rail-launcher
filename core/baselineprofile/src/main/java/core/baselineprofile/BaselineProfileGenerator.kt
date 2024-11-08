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

            // Scroll launcher
            device.wait(Until.hasObject(By.res("launcherList")), 5_000)
            val launcherList = device.findObject(By.res("launcherList"))
            launcherList.fling(Direction.DOWN, 10000)
            launcherList.fling(Direction.UP, 10000)

            // Interact with the ScrollRail
            device.wait(Until.hasObject(By.res("scrollRail")), 5_000)
            val scrollRail = device.findObject(By.res("scrollRail"))
            val point = Point(device.displayWidth, device.displayHeight)
            scrollRail.longClick()
            scrollRail.drag(point, 1000)

            // TODO Write more interactions to optimize advanced journeys of your app.
        }
    }
}
