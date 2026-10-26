package core.baselineprofile

import android.graphics.Point
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import kotlin.math.abs
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
private const val POPUP_CARD_TEST_TAG = "popupCard"
private const val POPUP_SWIPE_END_FRACTION = 0.9f
private const val POPUP_SWIPE_STEPS = 10
private const val POPUP_SCROLL_STEPS = 20
private const val POPUP_OPEN_REPEATS = 2
private const val STATUS_BAR_HEIGHT_FRACTION = 0.05f
private const val MIN_LAUNCHABLE_NAME_LENGTH = 2
private const val ROW_CENTER_SLOP_FRACTION = 0.02f

private val HEADER_TIME_SELECTOR: BySelector = By.descContains(HEADER_TIME_DESCRIPTION_FRAGMENT).pkg(APP_PACKAGE)
private val POPUP_CARD_SELECTOR: BySelector = By.res(POPUP_CARD_TEST_TAG)

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun generate() {
        val previousHome: String = previousHomeActivity()
        try {
            rule.collect(
                packageName = APP_PACKAGE,
                includeInStartupProfile = true
            ) {
                // Make Rail the default Home so pressHome() always returns to it.
                device.executeShellCommand("cmd package set-home-activity $APP_PACKAGE/.MainActivity")

                // Start default activity
                pressHome()
                startActivityAndWait()

                // Create a known Favorite, open it, exercise the card and return Home.
                findOrThrow(By.res("launcherHome"), "Home not found")
                val favoriteName: String = addFavoriteFromAppList()
                openFavoritePopup(favoriteName = favoriteName)

                // Scroll launcher
                val launcherList = openAppList()
                launcherList.fling(Direction.DOWN, FLING_SPEED)
                launcherList.fling(Direction.UP, FLING_SPEED)

                // Interact with the ScrollRail
                dragScrollRail()

                // Rail Settings > Widgets.
                setHeightLimit(row = openWidgetsSettings(), enabled = true)

                // Home with the widget height limit on, then scroll the widget area.
                returnHome()
                dragScrollRail()

                // Home with the widget height limit off.
                setHeightLimit(row = openWidgetsSettings(), enabled = false)
                returnHome()

                // The Header only renders in the uncapped Widget Area on a fresh install.
                check(scrollHomeToTop()) { "Header time not found on Home" }
                findOrThrow(HEADER_TIME_SELECTOR, "Header time not found on Home").longClick()
                findOrThrow(By.text(appString("clock_style")), "Header menu not found")
                device.pressBack()
                device.waitForIdle()

                // Media Player is hidden by default; exercise Home with no media player.
                check(device.wait(Until.gone(By.desc(appString("media_player_menu"))), WAIT_TIMEOUT_MS)) {
                    "Media Player unexpectedly visible; disable it in Widgets settings"
                }
                dragScrollRail()
            }
        } finally {
            restoreHome(previousHome = previousHome)
        }
    }

    private fun previousHomeActivity(): String = device.executeShellCommand(
        "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    ).lineSequence()
        .map(String::trim)
        .lastOrNull { line -> line.count { character -> character == '/' } == 1 && !line.startsWith("/") }
        .orEmpty()

    private fun restoreHome(previousHome: String) {
        if (previousHome.isNotBlank() && !previousHome.contains(APP_PACKAGE)) {
            device.executeShellCommand("cmd package set-home-activity $previousHome")
        }
    }

    private fun appString(name: String): String {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
            .getResourcesForApplication(APP_PACKAGE)
        val id: Int = resources.getIdentifier(name, "string", APP_PACKAGE)
        check(id != 0) { "String resource $name not found in $APP_PACKAGE" }
        return resources.getString(id)
    }

    private fun MacrobenchmarkScope.findOrThrow(selector: BySelector, message: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(selector), WAIT_TIMEOUT_MS)) { message }

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

    /** Scrolls Home up until its Header time is visible. */
    private fun MacrobenchmarkScope.scrollHomeToTop(): Boolean {
        val home = findOrThrow(By.res("launcherHome"), "Home not found")
        return scrollUntil(container = home, selector = HEADER_TIME_SELECTOR, direction = Direction.UP)
    }

    private fun MacrobenchmarkScope.openAppList(): UiObject2 {
        findOrThrow(By.res("scrollRail"), "ScrollRail not found").click()
        return findOrThrow(By.res("launcherList"), "Launcher list not found after opening the ScrollRail")
    }

    private fun MacrobenchmarkScope.openWidgetsSettings(): UiObject2 {
        val launcherList = openAppList()
        lastLaunchableNameNode(container = launcherList).click()
        findOrThrow(By.text(appString("widgets")), "Widgets entry not found").click()
        return findOrThrow(By.text(appString("limit_widget_area_height")), "Widgets settings not found")
    }

    private fun MacrobenchmarkScope.scrollUntil(container: UiObject2, selector: BySelector, direction: Direction): Boolean {
        repeat(SCROLL_ATTEMPTS) {
            if (device.hasObject(selector)) return true
            container.fling(direction, FLING_SPEED)
            device.waitForIdle()
        }
        return device.hasObject(selector)
    }

    private fun MacrobenchmarkScope.scrollToEnd(container: UiObject2) {
        repeat(SCROLL_ATTEMPTS) {
            container.fling(Direction.DOWN, FLING_SPEED)
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.lastLaunchableNameNode(container: UiObject2): UiObject2 {
        scrollToEnd(container = container)
        return checkNotNull(
            container.findObjects(By.clazz("android.widget.TextView"))
                .filter { node -> isLaunchableName(text = node.text) }
                .maxByOrNull { node -> node.visibleBounds.bottom }
        ) { "Rail Settings entry not found" }
    }

    private fun MacrobenchmarkScope.firstLaunchableNameNode(container: UiObject2): UiObject2 {
        val statusBarBottom: Int = (device.displayHeight * STATUS_BAR_HEIGHT_FRACTION).toInt()
        return checkNotNull(
            container.findObjects(By.clazz("android.widget.TextView"))
                .filter { node -> isLaunchableName(text = node.text) && node.visibleBounds.top >= statusBarBottom }
                .minByOrNull { node -> node.visibleBounds.top }
        ) { "App List row not found" }
    }

    private fun MacrobenchmarkScope.setHeightLimit(row: UiObject2, enabled: Boolean) {
        val onText: String = appString("widget_area_scrolls_when_needed")
        val offText: String = appString("home_scrolls_as_one_page")
        val isEnabled: Boolean = device.hasObject(By.text(onText))
        if (isEnabled != enabled) {
            row.click()
            device.waitForIdle()
        }
        val expectedText: String = if (enabled) onText else offText
        findOrThrow(By.text(expectedText), "Widget height limit did not switch to $enabled")
    }

    private fun MacrobenchmarkScope.dragScrollRail() {
        val scrollRail = findOrThrow(By.res("scrollRail"), "ScrollRail not found on Home")
        val point = Point(device.displayWidth, device.displayHeight)
        scrollRail.longClick()
        scrollRail.drag(point, DRAG_SPEED)
    }

    /** Creates or verifies a known Favorite and returns its Home label. */
    private fun MacrobenchmarkScope.addFavoriteFromAppList(): String {
        val launcherList = openAppList()
        val appNameNode = firstLaunchableNameNode(container = launcherList)
        val appName: String = checkNotNull(appNameNode.text) { "App List row has no name" }
        appNameNode.longClick()
        findOrThrow(By.text(appString("edit_favorites")), "Edit Favorites entry not found").click()

        val editName = findOrThrow(By.text(appName), "App row not found in Edit Favorites")
        val rowCenterY: Int = editName.visibleCenter.y
        val slop: Int = (device.displayHeight * ROW_CENTER_SLOP_FRACTION).toInt()
        val addStar: UiObject2? = favoriteStarNear(rowCenterY = rowCenterY, slop = slop, description = appString("add_to_favorites"))
        if (addStar != null) {
            addStar.click()
        } else {
            checkNotNull(
                favoriteStarNear(rowCenterY = rowCenterY, slop = slop, description = appString("remove_from_favorites"))
            ) { "Favorite star for $appName not found" }
        }
        findOrThrow(By.text(appString("done")), "Done button not found").click()
        val doneText: String = appString("done")
        check(device.wait(Until.gone(By.text(doneText)), WAIT_TIMEOUT_MS)) { "Edit Favorites did not close" }
        openHomeFromRail()
        findOrThrow(By.res("launcherHome"), "Home not found after adding a Favorite")
        check(
            !device.hasObject(By.desc(appString("add_to_favorites"))) &&
                !device.hasObject(By.desc(appString("remove_from_favorites")))
        ) { "Favorites edit did not close before opening the Pop-up" }
        findOrThrow(By.text(appName), "Favorite $appName not found on Home")
        return appName
    }

    /** Taps the Home entry at the top of the Rail without duplicating its label. */
    private fun MacrobenchmarkScope.openHomeFromRail() {
        val rail = findOrThrow(By.res("scrollRail"), "ScrollRail not found")
        val homeEntry = checkNotNull(
            rail.findObjects(By.clazz("android.widget.TextView")).minByOrNull { entry -> entry.visibleBounds.top }
        ) { "Home Rail entry not found" }
        homeEntry.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.favoriteStarNear(rowCenterY: Int, slop: Int, description: String): UiObject2? =
        device.findObjects(By.desc(description))
            .filter { star -> abs(star.visibleCenter.y - rowCenterY) <= slop }
            .minByOrNull { star -> abs(star.visibleCenter.y - rowCenterY) }

    private fun MacrobenchmarkScope.openFavoritePopup(favoriteName: String) {
        repeat(POPUP_OPEN_REPEATS) {
            val row = findOrThrow(By.text(favoriteName), "Favorite row not found")
            // A plain swipe: UiObject2.drag starts with a long press, which opens the item menu instead.
            device.swipe(
                row.visibleBounds.left,
                row.visibleCenter.y,
                (device.displayWidth * POPUP_SWIPE_END_FRACTION).toInt(),
                row.visibleCenter.y,
                POPUP_SWIPE_STEPS
            )
            scrollPopupCard()
            device.pressBack()
            check(device.wait(Until.gone(POPUP_CARD_SELECTOR), WAIT_TIMEOUT_MS)) {
                "Pop-up card did not close"
            }
        }
    }

    private fun MacrobenchmarkScope.scrollPopupCard() {
        val card = findOrThrow(POPUP_CARD_SELECTOR, "Pop-up card did not open")
        val bounds = card.visibleBounds
        device.swipe(
            bounds.centerX(),
            bounds.bottom - bounds.height() / 4,
            bounds.centerX(),
            bounds.top + bounds.height() / 4,
            POPUP_SCROLL_STEPS
        )
        device.waitForIdle()
    }

    private fun isLaunchableName(text: String?): Boolean = text != null && text.length >= MIN_LAUNCHABLE_NAME_LENGTH
}
