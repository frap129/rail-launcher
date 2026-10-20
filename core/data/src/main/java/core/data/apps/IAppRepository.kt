package core.data.apps

import core.data.apps.model.App
import kotlinx.coroutines.flow.StateFlow

interface IAppRepository {
    /** The installed apps, or null until the initial list has loaded. */
    val apps: StateFlow<List<App>?>
}
