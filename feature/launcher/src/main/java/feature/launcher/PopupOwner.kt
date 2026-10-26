package feature.launcher

import core.data.apps.model.App
import core.data.launchables.model.Launchable

/** The App [this] is, or null when it is another Launchable. */
internal val Launchable.appOwner: App?
    get() = this as? App
