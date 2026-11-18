package core.baselineprofile

import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Build
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
import android.view.View
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

private const val WAIT_TIMEOUT_MS = 5_000L
private const val FLING_SPEED = 10_000
private const val DRAG_SPEED = 1_000
private const val RAIL_SWIPE_STEPS = 10
private const val SCRUB_STRIP_EDGE_OFFSET_PX = 8
private const val SCRUB_STRIP_START_FRACTION = 0.25f
private const val SCRUB_STRIP_END_FRACTION = 0.75f
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
private const val NAVIGATION_BAR_HEIGHT_FRACTION = 0.06f
private const val REVEAL_SCROLL_PERCENT = 0.2f
private const val MIN_LAUNCHABLE_NAME_LENGTH = 2
private const val ROW_CENTER_SLOP_FRACTION = 0.02f
private const val RAIL_SETTINGS_NAME = "Rail Settings"
private const val SEEDED_FAVORITE_COUNT = 3
private const val ROW_READ_ATTEMPTS = 8
private const val ROW_READ_RETRY_DELAY_MS = 200L
private const val FAVORITE_STATE_ATTEMPTS = 5

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
    fun generateStartupProfile() {
        withPreviousHomeRestored(device = device) { previousHome ->
            val homeToHold: String = requireHomeToHold(device = device, previousHome = previousHome)
            warmStartupCaches(homeToHold = homeToHold)
            rule.collect(packageName = APP_PACKAGE, includeInStartupProfile = true) {
                collectStartupJourney(homeToHold = homeToHold)
            }
        }
    }

    @Test
    fun generateBaselineProfile() {
        withPreviousHomeRestored(device = device) {
            try {
                val testProfiles: TestProfiles = setUpTestProfiles()
                rule.collect(packageName = APP_PACKAGE, includeInStartupProfile = false) {
                    collectBroaderJourneys(testProfiles = testProfiles)
                }
            } finally {
                removeTestProfiles()
            }
        }
    }

    /**
     * Saves the Last-known App list and stores the seeded Favorites' icons before profile
     * collection starts. collect() kills the target process between iterations but never clears
     * its data or cache directory, so every collected run reads the warmed caches.
     */
    private fun warmStartupCaches(homeToHold: String) {
        setDefaultHomeAndVerify(device = device, homeActivity = APP_HOME_ACTIVITY)
        // A live start loads the Apps and saves the Last-known App list.
        device.pressHome()
        waitForHomeFullyDrawn(device = device)
        addFavoritesFromAppList(device = device, count = SEEDED_FAVORITE_COUNT)
        // The icons-drawn report is one-shot per process and the seeding process already reported
        // on its empty Home, so a fresh process is the observable signal that the seeded Favorites
        // and their stored icons loaded. The collected block then starts another fresh process.
        setDefaultHomeAndVerify(device = device, homeActivity = homeToHold)
        device.pressHome()
        device.executeShellCommand("am force-stop $APP_PACKAGE")
        setDefaultHomeAndVerify(device = device, homeActivity = APP_HOME_ACTIVITY)
        device.pressHome()
        waitForHomeFullyDrawn(device = device)
        waitForHomeIconsDrawn(device = device)
    }

    /** Covers the cold path to a usable Home, the only journey in the startup profile. */
    private fun MacrobenchmarkScope.collectStartupJourney(homeToHold: String) {
        // Hold another Home for the kill, so the system cannot restart Rail before the HOME press.
        setDefaultHomeAndVerify(device = device, homeActivity = homeToHold)
        pressHome()
        // killProcess() flushes ART profiles and waits inside the Benchmark library; the setup has
        // already stored the caches, so this wait is only for the profile flush.
        killProcess()
        setDefaultHomeAndVerify(device = device, homeActivity = APP_HOME_ACTIVITY)
        pressHome()
        waitForHomeFullyDrawn(device = device)
        waitForHomeIconsDrawn(device = device)
    }

    /** Covers the rest of the launcher for the full Baseline Profile. */
    private fun MacrobenchmarkScope.collectBroaderJourneys(testProfiles: TestProfiles) {
        // Make Rail the default Home so pressHome() always returns to it.
        setDefaultHomeAndVerify(device = device, homeActivity = APP_HOME_ACTIVITY)
        // Start default activity
        pressHome()
        startActivityAndWait()
        // Create a known Favorite, open it, exercise the card and return Home.
        findOrThrow(By.res("launcherHome"), "Home not found")
        val favoriteName: String = addFavoritesFromAppList(device = device, count = 1).single()
        openFavoritePopup(favoriteName = favoriteName)
        // Scroll launcher
        val launcherList = openAppList()
        launcherList.fling(Direction.DOWN, FLING_SPEED)
        launcherList.fling(Direction.UP, FLING_SPEED)
        // Interact with the ScrollRail
        dragScrollRail()
        dragScrubStrip()
        // Rail Settings > Widgets.
        setHeightLimit(row = openWidgetsSettings(), enabled = true)
        // Home with the widget height limit on: the Header renders inside the cap.
        returnHome()
        findOrThrow(HEADER_TIME_SELECTOR, "Header time not found in the capped Widget Area").longClick()
        findOrThrow(By.text(appString("clock_style")), "Header menu not found")
        device.pressBack()
        check(device.wait(Until.gone(By.text(appString("clock_style"))), WAIT_TIMEOUT_MS)) {
            "Header menu did not close"
        }
        dragScrollRail()
        // Home with the widget height limit off.
        setHeightLimit(row = openWidgetsSettings(), enabled = false)
        returnHome()
        check(scrollHomeToTop()) { "Header time not found on uncapped Home" }
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

    private fun MacrobenchmarkScope.findWorkRow(): UiObject2 = findListRow(
        device = device,
        container = findOrThrow(By.res("launcherList"), "App List not found"),
        selector = By.descStartsWith("${appString("work")} "),
        rowName = "Badged work row"
    )

    /**
     * Unlocks the Private Space through the system credential prompt, asserts the private Apps in
     * the Pop-up and Header pickers, locks the space again and asserts its Apps disappear.
     */
    private fun MacrobenchmarkScope.exercisePrivateSpace(privateSpaceUserId: Int, favoriteName: String) {
        val launcherList = openAppList()
        scrollToEnd(container = launcherList)
        val privateSpaceRowSelector: BySelector = By.text(appString("private_space"))
        val lockSelector: BySelector = By.text(appString("lock_private_space"))
        if (!device.hasObject(lockSelector)) {
            findOrThrow(privateSpaceRowSelector, "Private Space row not found").click()
            check(confirmPrivateSpaceCredential()) { "Private Space credential prompt did not close" }
            // Unlocking inserts the private Apps above the actions, which pushes them below the fold.
            scrollToEnd(container = launcherList)
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

    private fun MacrobenchmarkScope.findPrivateAppDescriptions(badgePrefix: String): Set<String> {
        val containerLabel: String = appString("private_space")
        return device.findObjects(By.clazz("android.widget.ImageView"))
            .mapNotNull { node -> node.contentDescription }
            .filter { description -> description.startsWith(badgePrefix) && description != containerLabel }
            .toSet()
    }

    private fun MacrobenchmarkScope.isPrivateAppInPicker(descriptions: Set<String>): Boolean {
        device.findObject(By.scrollable(true))?.let { pickerList ->
            scrollToEdge(device = device, container = pickerList, direction = Direction.DOWN)
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
        check(device.wait(Until.hasObject(By.text(appString("search"))), WAIT_TIMEOUT_MS)) {
            "Header tap action picker did not open"
        }
        val privateAppName: String = privateAppNames.first()
        findOrThrow(By.clazz("android.widget.EditText"), "Header tap action search not found").setText(privateAppName)
        findOrThrow(By.text(privateAppName), "Private App $privateAppName missing from the Header tap action picker")
        device.pressBack()
        if (!device.wait(Until.gone(By.text(appString("search"))), BACK_RETRY_TIMEOUT_MS)) {
            device.pressBack()
            check(device.wait(Until.gone(By.text(appString("search"))), WAIT_TIMEOUT_MS)) {
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
        findOrThrow(device = device, selector = selector, message = message)

    private fun findOrThrow(device: UiDevice, selector: BySelector, message: String): UiObject2 =
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

    private fun MacrobenchmarkScope.openAppList(): UiObject2 = openAppList(device = device)

    private fun openAppList(device: UiDevice): UiObject2 {
        findOrThrow(device = device, selector = By.res("scrollRail"), message = "ScrollRail not found").click()
        return findOrThrow(
            device = device,
            selector = By.res("launcherList"),
            message = "Launcher list not found after opening the ScrollRail"
        )
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
    ): Boolean = scrollUntil(device = device, container = container, selector = selector, direction = direction, speed = speed)

    private fun scrollUntil(
        device: UiDevice,
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

    private fun MacrobenchmarkScope.scrollToEnd(container: UiObject2) {
        scrollToEnd(device = device, container = container)
    }

    private fun scrollToStart(device: UiDevice, container: UiObject2) {
        scrollToEdge(device = device, container = container, direction = Direction.UP)
    }

    private fun scrollToEnd(device: UiDevice, container: UiObject2) {
        scrollToEdge(device = device, container = container, direction = Direction.DOWN)
    }

    // Stops at the edge, where a fling leaves the shown rows unchanged, instead of waiting out the
    // scroll-event timeout on every remaining attempt. fling()'s own result is not used: on a busy
    // device it misses the scroll event and reports the edge while the list is still moving.
    private fun scrollToEdge(device: UiDevice, container: UiObject2, direction: Direction) {
        repeat(SCROLL_ATTEMPTS) {
            val shownBefore: List<String>? = shownRows(device = device, container = container)
            container.fling(direction, FLING_SPEED)
            device.waitForIdle()
            val shownAfter: List<String>? = shownRows(device = device, container = container)
            if (shownBefore != null && shownAfter != null && shownAfter == shownBefore) {
                return
            }
        }
    }

    // A scroll can recycle a node while its text is read, so settle and re-read. Null means no
    // complete snapshot was readable: scrollToEdge must not mistake it for an unchanged edge.
    private fun shownRows(device: UiDevice, container: UiObject2): List<String>? {
        repeat(ROW_READ_ATTEMPTS) {
            try {
                return container.findObjects(By.clazz("android.widget.TextView"))
                    .map { node -> "${node.text}@${node.visibleBounds}" }
            } catch (exception: StaleObjectException) {
                device.waitForIdle()
                SystemClock.sleep(ROW_READ_RETRY_DELAY_MS)
            }
        }
        return null
    }

    /** Finds the built-in Rail Settings row by name, wherever the App List is scrolled. */
    private fun MacrobenchmarkScope.railSettingsEntry(container: UiObject2): UiObject2 = findListRow(
        device = device,
        container = container,
        selector = By.desc(RAIL_SETTINGS_NAME),
        rowName = "Rail Settings entry"
    )

    /**
     * Scrolls [container] from its start to the row matching [selector] and returns it once the
     * row's center is between the system bars: the App List draws under both bars, which take a
     * touch meant for a row there, so one short scroll toward the row brings it in first.
     */
    private fun findListRow(device: UiDevice, container: UiObject2, selector: BySelector, rowName: String): UiObject2 {
        scrollToStart(device = device, container = container)
        check(scrollUntil(device = device, container = container, selector = selector, direction = Direction.DOWN)) {
            "$rowName not found"
        }
        if (findTouchableRow(device = device, selector = selector) == null) {
            val isUnderStatusBar: Boolean = findOrThrow(device, selector, "$rowName not found").visibleBounds.centerY() <
                statusBarBottom(device)
            container.scroll(if (isUnderStatusBar) Direction.UP else Direction.DOWN, REVEAL_SCROLL_PERCENT)
            device.waitForIdle()
        }
        return checkNotNull(findTouchableRow(device = device, selector = selector)) { "$rowName is under a system bar" }
    }

    private fun findTouchableRow(device: UiDevice, selector: BySelector): UiObject2? {
        val navigationBarTop: Int = (device.displayHeight * (1 - NAVIGATION_BAR_HEIGHT_FRACTION)).toInt()
        return device.findObjects(selector).firstOrNull { node ->
            node.visibleBounds.centerY() in statusBarBottom(device)..navigationBarTop
        }
    }

    private fun statusBarBottom(device: UiDevice): Int = (device.displayHeight * STATUS_BAR_HEIGHT_FRACTION).toInt()

    // Personal and Work copies of an App share a label; distinct labels keep the seeded Favorites
    // distinct, since the Edit Favorites row is found by its label.
    private fun firstLaunchableNameNodes(device: UiDevice, container: UiObject2, count: Int): List<UiObject2> =
        container.findObjects(By.clazz("android.widget.TextView"))
            .filter { node -> isLaunchableName(text = node.text) && node.visibleBounds.top >= statusBarBottom(device) }
            .sortedBy { node -> node.visibleBounds.top }
            .distinctBy { node -> node.text }
            .take(count)

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

    /** Drags the invisible Rail scrub strip, which shares the drawn Rail's vertical bounds, along the layout start edge. */
    private fun MacrobenchmarkScope.dragScrubStrip() {
        val railBounds = findOrThrow(By.res("scrollRail"), "ScrollRail not found on Home").visibleBounds
        val startY: Int = (railBounds.top + railBounds.height() * SCRUB_STRIP_START_FRACTION).toInt()
        val endY: Int = (railBounds.top + railBounds.height() * SCRUB_STRIP_END_FRACTION).toInt()
        val isRtl: Boolean = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.layoutDirection ==
            View.LAYOUT_DIRECTION_RTL
        val stripX: Int = if (isRtl) device.displayWidth - SCRUB_STRIP_EDGE_OFFSET_PX else SCRUB_STRIP_EDGE_OFFSET_PX
        device.swipe(stripX, startY, stripX, endY, RAIL_SWIPE_STEPS)
        device.waitForIdle()
    }

    /** Creates or verifies the first [count] App List rows as Favorites and returns their Home labels. */
    private fun addFavoritesFromAppList(device: UiDevice, count: Int): List<String> {
        val launcherList: UiObject2 = openAppList(device = device)
        scrollToStart(device = device, container = launcherList)
        val appNameNodes: List<UiObject2> = firstLaunchableNameNodes(device = device, container = launcherList, count = count)
        check(appNameNodes.size == count) {
            "App List shows ${appNameNodes.size} distinct launchable rows below the status bar; " +
                "$count are required to seed the startup caches"
        }
        val appNames: List<String> = appNameNodes.map { node -> checkNotNull(node.text) { "App List row has no name" } }
        appNameNodes.first().longClick()
        findOrThrow(
            device = device,
            selector = By.text(appString("edit_favorites")),
            message = "Edit Favorites entry not found"
        ).click()
        val editList: UiObject2 = findOrThrow(
            device = device,
            selector = By.res("launcherList"),
            message = "App List not found in Edit Favorites"
        )
        val rowCenterSlop: Int = (device.displayHeight * ROW_CENTER_SLOP_FRACTION).toInt()
        appNames.forEach { appName ->
            toggleFavorite(device = device, launcherList = editList, appName = appName, rowCenterSlop = rowCenterSlop)
        }
        findOrThrow(device = device, selector = By.text(appString("done")), message = "Done button not found").click()
        val doneText: String = appString("done")
        check(device.wait(Until.gone(By.text(doneText)), WAIT_TIMEOUT_MS)) { "Edit Favorites did not close" }
        openHomeFromRail(device = device)
        findOrThrow(device = device, selector = By.res("launcherHome"), message = "Home not found after adding Favorites")
        check(
            !device.hasObject(By.desc(appString("add_to_favorites"))) &&
                !device.hasObject(By.desc(appString("remove_from_favorites")))
        ) { "Favorites edit did not close before opening the Pop-up" }
        appNames.forEach { appName ->
            findOrThrow(device = device, selector = By.text(appName), message = "Favorite $appName not found on Home")
        }
        return appNames
    }

    /** Adds [appName] to the Favorites, or verifies it already is one, and asserts the star's state. */
    private fun toggleFavorite(device: UiDevice, launcherList: UiObject2, appName: String, rowCenterSlop: Int) {
        val row: UiObject2 = findListRow(
            device = device,
            container = launcherList,
            selector = By.text(appName),
            rowName = "App $appName in Edit Favorites"
        )
        val addStar: UiObject2? = favoriteStarNear(
            device = device,
            rowCenterY = row.visibleCenter.y,
            slop = rowCenterSlop,
            description = appString("add_to_favorites")
        )
        if (addStar != null) {
            addStar.click()
            device.waitForIdle()
        }
        val updatedRow: UiObject2 = findListRow(
            device = device,
            container = launcherList,
            selector = By.text(appName),
            rowName = "App $appName in Edit Favorites"
        )
        checkNotNull(
            waitForFavoriteStar(
                device = device,
                rowCenterY = updatedRow.visibleCenter.y,
                slop = rowCenterSlop,
                description = appString("remove_from_favorites")
            )
        ) { "Favorite state for $appName did not change" }
    }

    private fun waitForFavoriteStar(device: UiDevice, rowCenterY: Int, slop: Int, description: String): UiObject2? {
        repeat(FAVORITE_STATE_ATTEMPTS) {
            favoriteStarNear(device = device, rowCenterY = rowCenterY, slop = slop, description = description)?.let { star ->
                return star
            }
            device.waitForIdle()
        }
        return null
    }

    /** Taps the Home entry at the top of the Rail without duplicating its label. */
    private fun openHomeFromRail(device: UiDevice) {
        val rail: UiObject2 = findOrThrow(device = device, selector = By.res("scrollRail"), message = "ScrollRail not found")
        val homeEntry: UiObject2 = checkNotNull(
            rail.findObjects(By.clazz("android.widget.TextView")).minByOrNull { entry -> entry.visibleBounds.top }
        ) { "Home Rail entry not found" }
        homeEntry.click()
        device.waitForIdle()
    }

    private fun favoriteStarNear(device: UiDevice, rowCenterY: Int, slop: Int, description: String): UiObject2? =
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
