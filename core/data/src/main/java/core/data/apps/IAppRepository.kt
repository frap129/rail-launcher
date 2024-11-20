package core.data.apps

import core.data.apps.model.App
import kotlinx.coroutines.flow.StateFlow

interface IAppRepository {
    val apps: StateFlow<List<App>>
}
