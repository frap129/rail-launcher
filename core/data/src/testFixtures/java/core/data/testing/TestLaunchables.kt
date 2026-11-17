package core.data.testing

import android.content.ComponentName
import core.data.apps.model.App
import core.data.icons.model.Icon
import core.data.launchables.LaunchableReadiness
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.profiles.ProfileToken

private const val TEST_APP_USER_ID: Int = 0
private const val TEST_APP_NAME: String = "App"
private const val TEST_APP_PACKAGE_NAME: String = "com.example.app"

/** A package name used for private Apps in tests. */
const val PRIVATE_PACKAGE_NAME: String = "com.example.private"

/** The Rail Settings Builtin group used across launcher tests. */
fun createBuiltinGroup(): LaunchableGroup.Builtin = LaunchableGroup.Builtin(items = listOf(createRailLaunchable("Rail Settings")))

/** A Rail Launchable with a predictable key for tests. */
fun createRailLaunchable(name: String, deeplink: String = "rail://$name"): RailLaunchable = RailLaunchable(
    defaultName = name,
    defaultIcon = Icon.RailIcon(resId = 0),
    deeplink = deeplink
)

/** A paused Gmail App in the Work profile. */
fun createPausedWorkApp(): App = createApp(
    name = "Gmail",
    packageName = "com.example.gmail",
    profileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work, isQuiet = true),
    badgedLabel = "Work Gmail"
)

/** An installed App with a predictable activity and key for tests, optionally renamed to [customName]. */
fun createApp(
    name: String = TEST_APP_NAME,
    packageName: String = TEST_APP_PACKAGE_NAME,
    profileInfo: ProfileInfo = createProfileInfo(userId = TEST_APP_USER_ID),
    badgedLabel: String = name,
    customName: String = name,
    firstInstallTime: Long = 0L,
    lastUpdateTime: Long = 0L,
    isRemovable: Boolean = true,
    isSuspended: Boolean = false
): App = App(
    defaultName = name,
    profileInfo = profileInfo,
    componentName = ComponentName(packageName, "$packageName.MainActivity"),
    badgedLabel = badgedLabel,
    name = customName,
    isPackageRepresentative = true,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
    isRemovable = isRemovable,
    isSuspended = isSuspended
)

/** The stored key format used before Apps were keyed by launcher activity: package name and Profile. */
fun App.packageLevelKey(): String = "${packageName}_$profile"

/** A private App in a Private Space Profile. */
fun createPrivateApp(name: String = "Secret", packageName: String = PRIVATE_PACKAGE_NAME, badgedLabel: String = "Private $name"): App =
    createApp(
        name = name,
        packageName = packageName,
        profileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private),
        badgedLabel = badgedLabel
    )

/** The Launchable State resolved from [groups] and [folders]. */
fun createLaunchableState(
    groups: List<LaunchableGroup>,
    folders: List<Launchable> = emptyList(),
    readiness: LaunchableReadiness = LaunchableReadiness.AppListReady,
    keptKeys: Set<ProfileToken> = emptySet(),
    retainedKeys: Set<String> = emptySet(),
    hiddenKeys: Set<String> = emptySet()
): LaunchableState = LaunchableState(
    groups = groups,
    launchables = groups.filterIsInstance<LaunchableGroup.Listed>().flatMap { group -> group.items } + folders,
    readiness = readiness,
    keptKeys = keptKeys,
    retainedKeys = retainedKeys,
    hiddenKeys = hiddenKeys
)

/** A Launchable State holding [launchables] in one Initial group, plus [folders]. */
fun createLoadedLaunchableState(
    launchables: List<Launchable>,
    folders: List<Launchable> = emptyList(),
    label: String = "A",
    readiness: LaunchableReadiness = LaunchableReadiness.AppListReady,
    keptKeys: Set<ProfileToken> = emptySet(),
    retainedKeys: Set<String> = emptySet(),
    hiddenKeys: Set<String> = emptySet()
): LaunchableState = createLaunchableState(
    groups = listOf(LaunchableGroup.InitialGroup(label = label, items = launchables)),
    folders = folders,
    readiness = readiness,
    keptKeys = keptKeys,
    retainedKeys = retainedKeys,
    hiddenKeys = hiddenKeys
)
