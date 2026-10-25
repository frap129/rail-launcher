package core.data.testing

import core.data.icons.model.Icon
import core.data.launchables.model.RailLaunchable

/** A Rail Launchable with a predictable key for tests. */
fun createRailLaunchable(name: String, deeplink: String = "rail://$name", resId: Int = 0): RailLaunchable = RailLaunchable(
    defaultName = name,
    defaultIcon = Icon.RailIcon(resId = resId),
    deeplink = deeplink
)
