package core.baselineprofile

import android.graphics.Point
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val APP_PACKAGE = "dev.maples.rail"
private const val WAIT_TIMEOUT_MS = 5_000L
private const val FLING_SPEED = 10_000
private const val DRAG_SPEED = 1_000
private const val BACK_RETRY_TIMEOUT_MS = 1_000L
private const val BACK_RETRIES = 3
private const val SCROLL_ATTEMPTS = 6
private const val HEADER_TIME_DESCRIPTION_FRAGMENT = ":"
private const val HEADER_MENU_INDICATOR_TEXT = "Clock Style"
private const val SETTINGS_ENTRY_TEXT = "Rail Settings"
private const val WIDGETS_ENTRY_TEXT = "Widgets"
private const val HEIGHT_LIMIT_ROW_TEXT = "Limit Widget Area height"
private const val HEIGHT_LIMIT_ON_TEXT = "The Widget Area scrolls when widgets don't fit"
private const val HEIGHT_LIMIT_OFF_TEXT = "Home scrolls as one page"
private const val MEDIA_PLAYER_MENU_DESC = "Media Player menu"

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = APP_PACKAGE,
            includeInStartupProfile = true
        ) {
            val previousHome: String = previousHomeActivity()
            try {
                // Make Rail the default Home so pressHome() always returns to it.
                device.executeShellCommand("cmd package set-home-activity $APP_PACKAGE/.MainActivity")

                // Start default activity
                pressHome()
                startActivityAndWait()

                // Open the app list from Home using the ScrollRail.
                findOrThrow(By.res("launcherHome"), "Home not found")
                findOrThrow(By.res("scrollRail"), "ScrollRail not found").click()

                // Scroll launcher
                val launcherList = findOrThrow(By.res("launcherList"), "Launcher list not found after opening the ScrollRail")
                launcherList.fling(Direction.DOWN, FLING_SPEED)
                launcherList.fling(Direction.UP, FLING_SPEED)

                // Interact with the ScrollRail
                dragScrollRail()

                // Header-only Home: open the Header menu.
                returnHome()
                headerTimeNode().longClick()
                findOrThrow(By.textContains(HEADER_MENU_INDICATOR_TEXT), "Header menu not found")
                device.pressBack()
                device.waitForIdle()

                // Rail Settings > Widgets.
                setHeightLimit(row = openWidgetsSettings(), enabled = true)

                // Home with the widget height limit on, then scroll the widget area.
                returnHome()
                dragScrollRail()

                // Home with the widget height limit off.
                setHeightLimit(row = openWidgetsSettings(), enabled = false)
                returnHome()

                // Media Player is hidden by default; exercise Home with no media player.
                check(device.wait(Until.gone(By.desc(MEDIA_PLAYER_MENU_DESC)), WAIT_TIMEOUT_MS)) {
                    "Media Player unexpectedly visible; disable it in Widgets settings"
                }
                dragScrollRail()
            } finally {
                restoreHome(previousHome)
            }
        }
    }

    private fun MacrobenchmarkScope.previousHomeActivity(): String = device.executeShellCommand(
        "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    ).lineSequence()
        .map(String::trim)
        .lastOrNull { line -> line.count { character -> character == '/' } == 1 && !line.startsWith("/") }
        .orEmpty()

    private fun MacrobenchmarkScope.restoreHome(previousHome: String) {
        if (previousHome.isNotBlank() && !previousHome.contains(APP_PACKAGE)) {
            device.executeShellCommand("cmd package set-home-activity $previousHome")
        }
    }

    private fun MacrobenchmarkScope.findOrThrow(selector: BySelector, message: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(selector), WAIT_TIMEOUT_MS)) { message }

    private fun MacrobenchmarkScope.headerTimeNode(): UiObject2 = checkNotNull(
        device.findObjects(By.descContains(HEADER_TIME_DESCRIPTION_FRAGMENT).pkg(APP_PACKAGE))
            .minByOrNull { node -> node.visibleBounds.top }
    ) {
        "Header time not found on Home"
    }

    private fun MacrobenchmarkScope.returnHome() {
        pressHome()
        if (device.wait(Until.hasObject(By.res("launcherHome")), WAIT_TIMEOUT_MS)) return
        repeat(BACK_RETRIES) {
            val scrollRail: UiObject2? = device.findObject(By.res("scrollRail"))
            if (scrollRail != null && device.findObject(By.res("launcherHome")) == null) {
                scrollRail.click()
                device.waitForIdle()
            }
            if (device.wait(Until.hasObject(By.res("launcherHome")), BACK_RETRY_TIMEOUT_MS)) return
            device.pressBack()
        }
        error("Home not found")
    }

    private fun MacrobenchmarkScope.openWidgetsSettings(): UiObject2 {
        findOrThrow(By.res("scrollRail"), "ScrollRail not found on Home").click()
        val launcherList = findOrThrow(By.res("launcherList"), "Launcher list not found")
        scrollToText(list = launcherList, text = SETTINGS_ENTRY_TEXT)
        findOrThrow(By.text(SETTINGS_ENTRY_TEXT), "Rail Settings entry not found").click()
        findOrThrow(By.text(WIDGETS_ENTRY_TEXT), "Widgets entry not found").click()
        return findOrThrow(By.text(HEIGHT_LIMIT_ROW_TEXT), "Widgets settings not found")
    }

    private fun MacrobenchmarkScope.scrollToText(list: UiObject2, text: String) {
        repeat(SCROLL_ATTEMPTS) {
            if (device.hasObject(By.text(text))) return
            list.fling(Direction.DOWN, FLING_SPEED)
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.setHeightLimit(row: UiObject2, enabled: Boolean) {
        val isEnabled: Boolean = device.hasObject(By.text(HEIGHT_LIMIT_ON_TEXT))
        if (isEnabled != enabled) {
            row.click()
            device.waitForIdle()
        }
        val expectedText: String = if (enabled) HEIGHT_LIMIT_ON_TEXT else HEIGHT_LIMIT_OFF_TEXT
        findOrThrow(By.text(expectedText), "Widget height limit did not switch to $enabled")
    }

    private fun MacrobenchmarkScope.dragScrollRail() {
        val scrollRail = findOrThrow(By.res("scrollRail"), "ScrollRail not found on Home")
        val point = Point(device.displayWidth, device.displayHeight)
        scrollRail.longClick()
        scrollRail.drag(point, DRAG_SPEED)
    }
}
