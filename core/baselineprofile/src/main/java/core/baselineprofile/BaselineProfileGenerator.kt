package core.baselineprofile

import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Build
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
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
import timber.log.Timber

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
private const val RAIL_SETTINGS_NAME = "Rail Settings"

private const val WORK_USER_NAME = "rail-work"
private const val PRIVATE_SPACE_USER_NAME = "rail-private"
private const val TEST_PIN = "1234"
private const val PIN_VERIFIED_OUTPUT = "verified successfully"
private const val PIN_SET_OUTPUT = "Pin set"
private const val PIN_CLEARED_OUTPUT = "cleared"
private const val MODIFY_QUIET_MODE_PERMISSION = "android.permission.MODIFY_QUIET_MODE"
private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
private const val QUIET_MODE_FLAG = "QUIET_MODE"
private const val RUNNING_FLAG = "(running)"
private const val PROFILE_POLL_INTERVAL_MS = 250L
private const val PROFILE_POLL_ATTEMPTS = 40
private const val PRIVATE_UNLOCK_TIMEOUT_MS = 10_000L
private const val KEYGUARD_ROOT_ID = "keyguard_root_view"
private const val KEYGUARD_PIN_VIEW_ID = "keyguard_pin_view"
private const val CREDENTIAL_INPUT_ID = "auth_credential_input"
private const val PIN_KEY_ID_PREFIX = "key"
private const val PIN_ENTER_KEY_ID = "key_enter"
private const val KEYGUARD_SWIPE_START_FRACTION = 0.8f
private const val KEYGUARD_SWIPE_END_FRACTION = 0.2f
private const val KEYGUARD_SWIPE_STEPS = 10
private const val LOCK_TAP_INSET = 4
private const val LOCK_TAP_ATTEMPTS = 3
private const val SETTINGS_FLING_SPEED = 2_000

private val HEADER_TIME_SELECTOR: BySelector = By.descContains(HEADER_TIME_DESCRIPTION_FRAGMENT).pkg(APP_PACKAGE)
private val POPUP_CARD_SELECTOR: BySelector = By.res(POPUP_CARD_TEST_TAG)
private val CREATED_USER_ID_REGEX: Regex = Regex("""Success: created user id (\d+)""")
private val USER_INFO_REGEX: Regex = Regex("""UserInfo\{(\d+):([^:]+):""")
private val USER_SERIAL_REGEX: Regex = Regex("""serialNo=(\d+)""")

private data class TestProfiles(val workUserId: Int?, val privateSpaceUserId: Int?)

private data class TestUser(val userId: Int, val isCreated: Boolean)

private data class DevicePinState(val isAvailable: Boolean, val isSetByRun: Boolean)

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val packageManager: PackageManager = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
    private val supportsWorkProfiles: Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)
    private val supportsPrivateSpace: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
    private val createdUserIds: MutableList<Int> = mutableListOf()
    private var isDevicePinSetByRun: Boolean = false

    init {
        Timber.plant(Timber.DebugTree())
    }

    @Test
    fun generate() {
        val previousHome: String = previousHomeActivity()
        try {
            val testProfiles: TestProfiles = setUpTestProfiles()
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

                testProfiles.workUserId?.let { workUserId ->
                    exerciseWorkProfile(workUserId = workUserId)
                }
                testProfiles.privateSpaceUserId?.let { privateSpaceUserId ->
                    exercisePrivateSpace(privateSpaceUserId = privateSpaceUserId, favoriteName = favoriteName)
                }
            }
        } finally {
            removeTestProfiles()
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

    /**
     * Creates the test Work profile and Private Space, installs Rail into both and locks the
     * Private Space. Existing test users are reused; users created here are removed afterwards.
     */
    private fun setUpTestProfiles(): TestProfiles {
        val workUserId: Int? = setUpWorkProfile()
        val privateSpaceUserId: Int? = setUpPrivateSpace()
        if (privateSpaceUserId != null) {
            val devicePin: DevicePinState = verifyOrSetDevicePin()
            isDevicePinSetByRun = devicePin.isSetByRun
            check(devicePin.isAvailable) { "Could not set or verify the test PIN" }
        }
        unlockDeviceIfNeeded()
        return TestProfiles(workUserId = workUserId, privateSpaceUserId = privateSpaceUserId)
    }

    private fun setUpWorkProfile(): Int? {
        if (!supportsWorkProfiles) {
            Timber.w("Device has no managed-profile support; skipping work paths")
            return null
        }
        val user: TestUser = ensureUser(name = WORK_USER_NAME, userType = UserManager.USER_TYPE_PROFILE_MANAGED)
        if (user.isCreated) {
            createdUserIds.add(user.userId)
        }
        resumeWorkProfile(userId = user.userId)
        installAppForUser(userId = user.userId)
        return user.userId
    }

    private fun setUpPrivateSpace(): Int? {
        if (!supportsPrivateSpace) {
            Timber.w("Device has no Private Space support; skipping private paths")
            return null
        }
        val user: TestUser = ensureUser(name = PRIVATE_SPACE_USER_NAME, userType = UserManager.USER_TYPE_PROFILE_PRIVATE)
        if (user.isCreated) {
            createdUserIds.add(user.userId)
        }
        installAppForUser(userId = user.userId)
        setQuietMode(userId = user.userId, enabled = true)
        return user.userId
    }

    private fun ensureUser(name: String, userType: String): TestUser {
        findUserId(name = name)?.let { userId -> return TestUser(userId = userId, isCreated = false) }
        val output: String = device.executeShellCommand("pm create-user --profileOf 0 --user-type $userType $name")
        val userId: Int? = CREATED_USER_ID_REGEX.find(output)?.groupValues?.get(1)?.toIntOrNull()
        checkNotNull(userId) { "Could not create the $name user: $output" }
        return TestUser(userId = userId, isCreated = true)
    }

    private fun findUserId(name: String): Int? = device.executeShellCommand("pm list users").lineSequence()
        .mapNotNull { line -> USER_INFO_REGEX.find(line) }
        .firstOrNull { match -> match.groupValues[2] == name }
        ?.groupValues?.get(1)?.toIntOrNull()

    private fun resumeWorkProfile(userId: Int) {
        if (isUserFlagSet(userId = userId, flag = QUIET_MODE_FLAG)) {
            setQuietMode(userId = userId, enabled = false)
            return
        }
        device.executeShellCommand("am start-user -w $userId")
        check(waitForUserFlag(userId = userId, flag = RUNNING_FLAG, expected = true)) {
            "The Work profile did not start"
        }
    }

    private fun installAppForUser(userId: Int) {
        val output: String = device.executeShellCommand("pm install-existing --user $userId $APP_PACKAGE")
        check(output.contains("installed for user")) { "Could not install $APP_PACKAGE for user $userId: $output" }
    }

    private fun verifyOrSetDevicePin(): DevicePinState {
        if (device.executeShellCommand("locksettings verify --old $TEST_PIN").contains(PIN_VERIFIED_OUTPUT)) {
            return DevicePinState(isAvailable = true, isSetByRun = false)
        }
        val isSet: Boolean = device.executeShellCommand("locksettings set-pin $TEST_PIN").contains(PIN_SET_OUTPUT)
        return DevicePinState(isAvailable = isSet, isSetByRun = isSet)
    }

    private fun removeTestProfiles() {
        val failures: MutableList<String> = mutableListOf()
        createdUserIds.forEach { userId ->
            val output: String = device.executeShellCommand("pm remove-user $userId")
            if (!output.contains("Success")) {
                failures.add("user $userId: $output")
            }
        }
        if (isDevicePinSetByRun) {
            val output: String = device.executeShellCommand("locksettings clear --old $TEST_PIN")
            if (!output.contains(PIN_CLEARED_OUTPUT)) {
                failures.add("PIN: $output")
            }
        }
        check(failures.isEmpty()) { "Could not clean up test profiles: ${failures.joinToString(separator = "; ")}" }
    }

    private fun unlockDeviceIfNeeded() {
        if (!device.hasObject(By.res(SYSTEM_UI_PACKAGE, KEYGUARD_ROOT_ID))) {
            return
        }
        if (!device.hasObject(By.res(SYSTEM_UI_PACKAGE, KEYGUARD_PIN_VIEW_ID))) {
            val centerX: Int = device.displayWidth / 2
            device.swipe(
                centerX,
                (device.displayHeight * KEYGUARD_SWIPE_START_FRACTION).toInt(),
                centerX,
                (device.displayHeight * KEYGUARD_SWIPE_END_FRACTION).toInt(),
                KEYGUARD_SWIPE_STEPS
            )
            device.waitForIdle()
        }
        if (!device.hasObject(By.res(SYSTEM_UI_PACKAGE, KEYGUARD_PIN_VIEW_ID))) {
            return
        }
        TEST_PIN.forEach { digit ->
            checkNotNull(
                device.wait(Until.findObject(By.res(SYSTEM_UI_PACKAGE, "$PIN_KEY_ID_PREFIX$digit")), WAIT_TIMEOUT_MS)
            ) { "PIN key $digit not found" }.click()
        }
        checkNotNull(device.wait(Until.findObject(By.res(SYSTEM_UI_PACKAGE, PIN_ENTER_KEY_ID)), WAIT_TIMEOUT_MS)) {
            "PIN Enter key not found"
        }.click()
        device.waitForIdle()
    }

    /** Opens a work row's menu, then pauses the profile and resumes it through the paused prompt. */
    private fun MacrobenchmarkScope.exerciseWorkProfile(workUserId: Int) {
        val workRow: UiObject2 = findWorkRow()
        workRow.longClick()
        findOrThrow(By.text(appString("edit_favorites")), "Work App menu did not open")
        device.pressBack()
        check(device.wait(Until.gone(By.text(appString("edit_favorites"))), WAIT_TIMEOUT_MS)) {
            "Work App menu did not close"
        }
        setQuietMode(userId = workUserId, enabled = true)
        findWorkRow().click()
        findOrThrow(By.text(appString("work_apps_paused")), "The paused Work prompt did not open")
        findOrThrow(By.text(appString("turn_on")), "The Turn on action was not offered").click()
        check(waitForUserFlag(userId = workUserId, flag = QUIET_MODE_FLAG, expected = false)) {
            "The Work profile did not resume"
        }
        check(device.wait(Until.gone(By.text(appString("work_apps_paused"))), WAIT_TIMEOUT_MS)) {
            "The paused Work prompt did not close"
        }
        returnHome()
    }

    private fun MacrobenchmarkScope.findWorkRow(): UiObject2 {
        val selector: BySelector = By.descStartsWith("${appString("work")} ")
        val launcherList = findOrThrow(By.res("launcherList"), "App List not found")
        scrollToStart(container = launcherList)
        check(scrollUntil(container = launcherList, selector = selector, direction = Direction.DOWN)) {
            "No badged work row in the App List"
        }
        return findOrThrow(selector, "No badged work row in the App List")
    }

    /**
     * Unlocks the Private Space through the system credential prompt, asserts the private Apps in
     * the Pop-up and Header pickers, locks the space again and asserts its Apps disappear.
     */
    private fun MacrobenchmarkScope.exercisePrivateSpace(privateSpaceUserId: Int, favoriteName: String) {
        val launcherList = openAppList()
        scrollToEnd(container = launcherList)
        val privateSpaceSelector: BySelector = By.desc(appString("private_space")).clickable(true)
        val lockSelector: BySelector = By.desc(appString("lock_private_space"))
        if (!device.hasObject(lockSelector)) {
            findOrThrow(privateSpaceSelector, "Private Space row not found").click()
            check(confirmPrivateSpaceCredential()) { "Private Space credential prompt did not close" }
        }
        check(device.wait(Until.hasObject(lockSelector), PRIVATE_UNLOCK_TIMEOUT_MS)) { "Private Space did not unlock" }
        val privateBadgePrefix: String = privateAppBadgePrefix(privateSpaceUserId = privateSpaceUserId)
        val privateAppDescriptions: Set<String> = buildSet {
            addAll(findPrivateAppDescriptions(badgePrefix = privateBadgePrefix))
            scrollToEnd(container = launcherList)
            addAll(findPrivateAppDescriptions(badgePrefix = privateBadgePrefix))
        }
        check(privateAppDescriptions.isNotEmpty()) { "No badged private App row found" }
        val privateAppNames: Set<String> = privateAppDescriptions
            .map { description -> description.removePrefix(privateBadgePrefix) }
            .filter { name -> name.isNotBlank() }
            .toSet()
        check(privateAppNames.isNotEmpty()) { "Private App rows have no labels" }

        returnHome()
        openFavoriteCard(favoriteName = favoriteName)
        findOrThrow(POPUP_CARD_SELECTOR, "Pop-up card did not open").longClick()
        findOrThrow(By.text(appString("edit_popup")), "Edit Pop-up action not found").click()
        findOrThrow(By.text(appString("add_apps")), "Add apps not found").click()
        check(device.wait(Until.hasObject(By.text(appString("add"))), WAIT_TIMEOUT_MS)) { "Add apps picker did not open" }
        check(isPrivateAppInPicker(descriptions = privateAppDescriptions)) {
            "Private apps missing from the Add apps picker"
        }
        device.pressBack()
        check(device.wait(Until.gone(By.text(appString("add"))), WAIT_TIMEOUT_MS)) { "Add apps picker did not close" }
        findOrThrow(By.text(appString("add_shortcuts")), "Add shortcuts not found").click()
        check(device.wait(Until.hasObject(By.text(appString("add"))), WAIT_TIMEOUT_MS)) { "Shortcut picker did not open" }
        check(isPrivateAppInPicker(descriptions = privateAppDescriptions)) {
            "Private apps missing from the Shortcut picker"
        }
        device.pressBack()
        check(device.wait(Until.gone(By.text(appString("add"))), WAIT_TIMEOUT_MS)) { "Shortcut picker did not close" }
        findOrThrow(By.text(appString("done")), "Edit Pop-up Done not found").click()
        check(device.wait(Until.gone(By.text(appString("add_shortcuts"))), WAIT_TIMEOUT_MS)) {
            "Edit Pop-up did not close"
        }
        device.pressBack()
        check(device.wait(Until.gone(POPUP_CARD_SELECTOR), WAIT_TIMEOUT_MS)) { "Pop-up card did not close" }

        exerciseHeaderTapActionPicker(privateAppNames = privateAppNames)
        returnHome()
        val lockList = openAppList()
        clickPrivateSpaceLock(
            launcherList = lockList,
            privateSpaceUserId = privateSpaceUserId,
            lockSelector = lockSelector
        )
        check(device.wait(Until.gone(lockSelector), WAIT_TIMEOUT_MS)) { "Private Space Lock action stayed visible" }
        privateAppDescriptions.forEach { description ->
            check(device.wait(Until.gone(By.desc(description)), WAIT_TIMEOUT_MS)) {
                "Private App $description stayed listed while locked"
            }
        }
        returnHome()
    }

    /**
     * Taps the Lock action without hitting the ScrollRail, which covers the button's centre while
     * the list is scrolled far enough; falls back to the exposed top or bottom edge.
     */
    private fun MacrobenchmarkScope.clickPrivateSpaceLock(launcherList: UiObject2, privateSpaceUserId: Int, lockSelector: BySelector) {
        scrollToEnd(container = launcherList)
        check(scrollUntil(container = launcherList, selector = lockSelector, direction = Direction.UP)) {
            "Private Space Lock action not found"
        }
        repeat(LOCK_TAP_ATTEMPTS) {
            val lock: UiObject2 = findOrThrow(lockSelector, "Private Space Lock action not found")
            val railBounds = findOrThrow(By.res("scrollRail"), "ScrollRail not found").visibleBounds
            when {
                lock.visibleBounds.top < railBounds.top -> device.click(
                    lock.visibleCenter.x,
                    lock.visibleBounds.top + LOCK_TAP_INSET
                )

                lock.visibleBounds.bottom > railBounds.bottom -> device.click(
                    lock.visibleCenter.x,
                    lock.visibleBounds.bottom - LOCK_TAP_INSET
                )

                else -> {
                    launcherList.fling(Direction.UP, FLING_SPEED)
                    device.waitForIdle()
                    return@repeat
                }
            }
            if (waitForUserFlag(userId = privateSpaceUserId, flag = QUIET_MODE_FLAG, expected = true)) return
        }
        error("Private Space did not lock")
    }

    /** Derives the system badge the Private Space puts in front of a private App's label. */
    private fun privateAppBadgePrefix(privateSpaceUserId: Int): String {
        val appName: String = appString("app_name")
        val badgedName: String = packageManager.getUserBadgedLabel(appName, userHandle(userId = privateSpaceUserId)).toString()
        val appNameIndex: Int = badgedName.indexOf(appName)
        check(appNameIndex > 0) { "Private Apps are not badged on this device: $badgedName" }
        return badgedName.substring(startIndex = 0, endIndex = appNameIndex)
    }

    private fun MacrobenchmarkScope.findPrivateAppDescriptions(badgePrefix: String): Set<String> =
        device.findObjects(By.clazz("android.widget.ImageView"))
            .mapNotNull { node -> node.contentDescription }
            .filter { description -> description.startsWith(badgePrefix) }
            .toSet()

    private fun MacrobenchmarkScope.isPrivateAppInPicker(descriptions: Set<String>): Boolean {
        device.findObject(By.scrollable(true))?.let { pickerList ->
            scrollToEdge(container = pickerList, direction = Direction.DOWN)
        }
        return descriptions.any { description -> device.hasObject(By.desc(description)) }
    }

    /** Opens the Header tap action picker while Private Space is unlocked and backs out unchanged. */
    private fun MacrobenchmarkScope.exerciseHeaderTapActionPicker(privateAppNames: Set<String>) {
        openWidgetsSettings()
        val settingsList = widgetsSettingsList()
        check(
            scrollUntil(
                container = settingsList,
                selector = By.text(appString("time_tap_action")),
                direction = Direction.DOWN,
                speed = SETTINGS_FLING_SPEED
            )
        ) { "Time tap action not found" }
        findOrThrow(By.text(appString("time_tap_action")), "Time tap action not found").click()
        check(device.wait(Until.hasObject(By.text(appString("tap_action_search"))), WAIT_TIMEOUT_MS)) {
            "Header tap action picker did not open"
        }
        val privateAppName: String = privateAppNames.first()
        findOrThrow(By.clazz("android.widget.EditText"), "Header tap action search not found").setText(privateAppName)
        findOrThrow(By.text(privateAppName), "Private App $privateAppName missing from the Header tap action picker")
        device.pressBack()
        if (!device.wait(Until.gone(By.text(appString("tap_action_search"))), BACK_RETRY_TIMEOUT_MS)) {
            device.pressBack()
            check(device.wait(Until.gone(By.text(appString("tap_action_search"))), WAIT_TIMEOUT_MS)) {
                "Header tap action picker did not close"
            }
        }
        device.pressBack()
        returnHome()
    }

    private fun MacrobenchmarkScope.widgetsSettingsList(): UiObject2 = checkNotNull(
        device.findObjects(By.scrollable(true)).firstOrNull { list ->
            list.findObject(By.text(appString("limit_widget_area_height"))) != null
        }
    ) { "Widgets settings list not found" }

    private fun MacrobenchmarkScope.confirmPrivateSpaceCredential(): Boolean {
        val inputSelector: BySelector = By.res(SYSTEM_UI_PACKAGE, CREDENTIAL_INPUT_ID)
        if (!device.wait(Until.hasObject(inputSelector), PRIVATE_UNLOCK_TIMEOUT_MS)) {
            return false
        }
        device.executeShellCommand("input text $TEST_PIN")
        device.pressEnter()
        return device.wait(Until.gone(inputSelector), PRIVATE_UNLOCK_TIMEOUT_MS)
    }

    /** Changes a Profile's quiet mode through the public API under the shell's quiet-mode permission. */
    private fun setQuietMode(userId: Int, enabled: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val userManager: UserManager = instrumentation.targetContext.getSystemService(UserManager::class.java)
        val uiAutomation = instrumentation.uiAutomation
        val user: UserHandle = userHandle(userId = userId)
        uiAutomation.adoptShellPermissionIdentity(MODIFY_QUIET_MODE_PERMISSION)
        val isChanged: Boolean
        try {
            isChanged = userManager.requestQuietModeEnabled(enabled, user)
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
        check(isChanged) { "Could not set quiet mode to $enabled for user $userId" }
        check(waitForUserFlag(userId = userId, flag = QUIET_MODE_FLAG, expected = enabled)) {
            "Quiet mode for user $userId did not become $enabled"
        }
    }

    private fun userHandle(userId: Int): UserHandle {
        val userLine: String = device.executeShellCommand("dumpsys user").lineSequence()
            .firstOrNull { line -> line.trim().startsWith("UserInfo{$userId:") }
            ?: error("No user info for user $userId")
        val serialNumber: Long = USER_SERIAL_REGEX.find(userLine)?.groupValues?.get(1)?.toLongOrNull()
            ?: error("No serial number for user $userId")
        val userManager: UserManager = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(UserManager::class.java)
        return checkNotNull(userManager.getUserForSerialNumber(serialNumber)) {
            "No UserHandle for user $userId"
        }
    }

    private fun waitForUserFlag(userId: Int, flag: String, expected: Boolean): Boolean {
        repeat(PROFILE_POLL_ATTEMPTS) {
            if (isUserFlagSet(userId = userId, flag = flag) == expected) {
                return true
            }
            SystemClock.sleep(PROFILE_POLL_INTERVAL_MS)
        }
        return isUserFlagSet(userId = userId, flag = flag) == expected
    }

    private fun isUserFlagSet(userId: Int, flag: String): Boolean = userInfoLine(userId = userId)?.contains(flag) == true

    private fun userInfoLine(userId: Int): String? = device.executeShellCommand("cmd user list -v").lineSequence()
        .firstOrNull { line -> line.contains("id=$userId,") }

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
        railSettingsEntry(container = launcherList).click()
        findOrThrow(By.text(appString("widgets")), "Widgets entry not found").click()
        return findOrThrow(By.text(appString("limit_widget_area_height")), "Widgets settings not found")
    }

    private fun MacrobenchmarkScope.scrollUntil(
        container: UiObject2,
        selector: BySelector,
        direction: Direction,
        speed: Int = FLING_SPEED
    ): Boolean {
        repeat(SCROLL_ATTEMPTS) {
            if (device.hasObject(selector)) return true
            container.fling(direction, speed)
            device.waitForIdle()
        }
        return device.hasObject(selector)
    }

    private fun MacrobenchmarkScope.scrollToStart(container: UiObject2) {
        scrollToEdge(container = container, direction = Direction.UP)
    }

    private fun MacrobenchmarkScope.scrollToEnd(container: UiObject2) {
        scrollToEdge(container = container, direction = Direction.DOWN)
    }

    private fun MacrobenchmarkScope.scrollToEdge(container: UiObject2, direction: Direction) {
        repeat(SCROLL_ATTEMPTS) {
            container.fling(direction, FLING_SPEED)
            device.waitForIdle()
        }
    }

    /** Finds the built-in Rail Settings row by name, wherever the App List is scrolled. */
    private fun MacrobenchmarkScope.railSettingsEntry(container: UiObject2): UiObject2 {
        val selector: BySelector = By.desc(RAIL_SETTINGS_NAME)
        scrollToStart(container = container)
        check(scrollUntil(container = container, selector = selector, direction = Direction.DOWN)) {
            "Rail Settings entry not found"
        }
        return findOrThrow(selector, "Rail Settings entry not found")
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

    private fun MacrobenchmarkScope.openFavoriteCard(favoriteName: String) {
        val row = findOrThrow(By.text(favoriteName), "Favorite row not found")
        // A plain swipe: UiObject2.drag starts with a long press, which opens the item menu instead.
        device.swipe(
            row.visibleBounds.left,
            row.visibleCenter.y,
            (device.displayWidth * POPUP_SWIPE_END_FRACTION).toInt(),
            row.visibleCenter.y,
            POPUP_SWIPE_STEPS
        )
    }

    private fun MacrobenchmarkScope.openFavoritePopup(favoriteName: String) {
        repeat(POPUP_OPEN_REPEATS) {
            openFavoriteCard(favoriteName = favoriteName)
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
