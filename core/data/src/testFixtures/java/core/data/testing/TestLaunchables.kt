package core.data.testing

import android.content.ComponentName
import android.os.UserHandle
import core.data.apps.model.App
import core.data.icons.model.Icon
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable

private const val TEST_APP_USER_ID: Int = 0

/** A Rail Launchable with a predictable key for tests. */
fun createRailLaunchable(name: String, deeplink: String = "rail://$name", resId: Int = 0): RailLaunchable = RailLaunchable(
    defaultName = name,
    defaultIcon = Icon.RailIcon(resId = resId),
    deeplink = deeplink
)

/** An installed App with a predictable activity and key for tests. */
fun createApp(name: String, packageName: String, profile: UserHandle = UserHandle.getUserHandleForUid(TEST_APP_USER_ID)): App = App(
    defaultName = name,
    profile = profile,
    componentName = ComponentName(packageName, "$packageName.MainActivity")
)

/** The Launchable State resolved from [groups] and [folders]. */
fun createLaunchableState(
    groups: List<LaunchableGroup>,
    folders: List<Launchable> = emptyList(),
    isLoaded: Boolean = true
): LaunchableState = LaunchableState(
    groups = groups,
    launchables = groups.filterIsInstance<LaunchableGroup.Listed>().flatMap { group -> group.items } + folders,
    isLoaded = isLoaded
)

/** A loaded Launchable State holding [launchables] in one Initial group, plus [folders]. */
fun createLoadedLaunchableState(
    launchables: List<Launchable>,
    folders: List<Launchable> = emptyList(),
    label: String = "A",
    isLoaded: Boolean = true
): LaunchableState = createLaunchableState(
    groups = listOf(LaunchableGroup.InitialGroup(label = label, items = launchables)),
    folders = folders,
    isLoaded = isLoaded
)
